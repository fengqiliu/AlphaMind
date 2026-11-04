package com.alphamind.news.service;

import com.alphamind.news.provider.EastmoneyNewsProvider;
import com.alphamind.news.provider.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import com.alphamind.archive.ArchiveService;
import com.alphamind.config.ConcurrencyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 新闻读取顺序：新鲜缓存 → 真实 Provider → 明示的过期缓存 → empty。
 * 不在这里编造新闻标题或数量，避免下游把伪数据当作真实舆情使用。
 */
@Slf4j
@Service
public class StockNewsService {

    private static final int DEFAULT_LIMIT = 20;

    private final EastmoneyNewsProvider newsProvider;
    private final NewsCacheService cacheService;

    /** 正文按链接缓存：文章发布后基本不变；只缓存成功结果，失败下次重试。 */
    static final Duration BODY_TTL = Duration.ofHours(24);
    static final int BODY_CACHE_MAX = 500;
    /** 为了在个别文章失败时仍凑够 limit 篇，最多并发尝试 limit 的这个倍数。 */
    static final int BODY_CANDIDATE_FACTOR = 2;

    private final Executor executor;
    private final ArchiveService archive;
    private final Map<String, CachedBody> bodyCache = new ConcurrentHashMap<>();

    public StockNewsService(EastmoneyNewsProvider newsProvider, NewsCacheService cacheService) {
        this(newsProvider, cacheService, Runnable::run);
    }

    @Autowired
    public StockNewsService(EastmoneyNewsProvider newsProvider, NewsCacheService cacheService,
                            ConcurrencyConfig.IoExecutor io, ArchiveService archive) {
        this(newsProvider, cacheService, io.executor(), archive);
    }

    public StockNewsService(EastmoneyNewsProvider newsProvider, NewsCacheService cacheService, Executor executor) {
        this(newsProvider, cacheService, executor, null);
    }

    /** {@code archive} 可为空（测试）；有值时每次真实抓到新闻都会旁路存档。 */
    public StockNewsService(EastmoneyNewsProvider newsProvider, NewsCacheService cacheService, Executor executor,
                            ArchiveService archive) {
        this.newsProvider = newsProvider;
        this.cacheService = cacheService;
        this.executor = executor;
        this.archive = archive;
    }

    /** 读取个股新闻；新闻源与缓存都不可用时返回 empty，绝不抛出。 */
    public Optional<NewsResolution> news(String stockCode) {
        return news(stockCode, DEFAULT_LIMIT);
    }

    public Optional<NewsResolution> news(String stockCode, int limit) {
        return cacheService.getFresh(stockCode)
                .map(hit -> new NewsResolution(
                        hit.snapshot(), NewsResolution.Status.FRESH_CACHE,
                        hit.ageSeconds(), null))
                .or(() -> fetchProviderOrStale(stockCode, limit));
    }

    /** 只存档真实抓取的快照；缓存读取不重复存档。存档失败不影响新闻读取。 */
    private void archiveQuietly(NewsSnapshot snapshot) {
        if (archive == null) {
            return;
        }
        try {
            archive.archiveNews(snapshot);
        } catch (RuntimeException e) {
            log.warn("新闻存档异常（已忽略）: stockCode={}, reason={}", snapshot.stockCode(), e.getMessage());
        }
    }

    private Optional<NewsResolution> fetchProviderOrStale(String stockCode, int limit) {
        Optional<List<NewsArticle>> fetched = newsProvider.fetchNews(stockCode, limit);
        if (fetched.isPresent()) {
            NewsSnapshot snapshot = new NewsSnapshot(
                    stockCode, fetched.get(), newsProvider.sourceName(), Instant.now());
            cacheService.put(snapshot);
            archiveQuietly(snapshot);
            return Optional.of(new NewsResolution(snapshot, NewsResolution.Status.LIVE, 0, null));
        }

        return cacheService.getStale(stockCode)
                .map(hit -> {
                    String warning = "新闻源不可用，当前展示最后成功缓存（"
                            + hit.ageSeconds() + " 秒前）";
                    log.warn("{}: stockCode={}", warning, stockCode);
                    return new NewsResolution(
                            hit.snapshot(), NewsResolution.Status.STALE_CACHE,
                            hit.ageSeconds(), warning);
                });
    }

    /**
     * 为前 limit 篇带链接的新闻抓取正文摘录，返回 标题 → 正文。
     * 单篇失败只是缺席对应条目，绝不抛出、绝不编造正文。
     */
    /**
     * 在后台读取新闻并预热前 {@code bodyLimit} 篇正文缓存，供分析流水线在行情/技术阶段并行准备。
     * 与 {@link #news(String)} 一样不会抛出；失败时 future 结果为 empty。
     */
    public CompletableFuture<Optional<NewsResolution>> prefetch(String stockCode, int bodyLimit, int maxChars) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NewsResolution> resolution = news(stockCode);
            resolution.ifPresent(r -> articleBodiesByTitle(r.snapshot().articles(), bodyLimit, maxChars));
            return resolution;
        }, executor).exceptionally(e -> {
            log.warn("新闻预取失败，舆情阶段将重新读取: stockCode={}, reason={}", stockCode, e.getMessage());
            return Optional.empty();
        });
    }

    public Map<String, String> articleBodiesByTitle(List<NewsArticle> articles, int limit, int maxChars) {
        Map<String, String> bodies = new LinkedHashMap<>();
        if (articles == null || limit <= 0) {
            return bodies;
        }
        List<NewsArticle> candidates = articles.stream()
                .filter(article -> article != null && article.url() != null && article.title() != null)
                .limit((long) limit * BODY_CANDIDATE_FACTOR)
                .toList();
        // 并发抓取候选文章，再按原有顺序取前 limit 篇成功的结果
        List<CompletableFuture<Optional<String>>> futures = candidates.stream()
                .map(article -> body(article.url(), maxChars))
                .toList();
        for (int i = 0; i < candidates.size() && bodies.size() < limit; i++) {
            String title = candidates.get(i).title();
            futures.get(i).join().ifPresent(body -> bodies.putIfAbsent(title, body));
        }
        return bodies;
    }

    private CompletableFuture<Optional<String>> body(String url, int maxChars) {
        String key = maxChars + "|" + url;
        CachedBody cached = bodyCache.get(key);
        if (cached != null && cached.fetchedAt().plus(BODY_TTL).isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Optional.of(cached.body()));
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> fetched;
            try {
                fetched = newsProvider.fetchArticleBody(url, maxChars);
            } catch (RuntimeException e) {
                log.warn("新闻正文抓取失败，跳过该篇: url={}, reason={}", url, e.getMessage());
                fetched = Optional.empty();
            }
            fetched.ifPresent(body -> remember(key, body));
            return fetched;
        }, executor);
    }

    private void remember(String key, String body) {
        if (bodyCache.size() >= BODY_CACHE_MAX) {
            Instant cutoff = Instant.now().minus(BODY_TTL);
            bodyCache.values().removeIf(entry -> entry.fetchedAt().isBefore(cutoff));
            if (bodyCache.size() >= BODY_CACHE_MAX) {
                bodyCache.entrySet().stream()
                        .min(Map.Entry.comparingByValue(java.util.Comparator.comparing(CachedBody::fetchedAt)))
                        .ifPresent(oldest -> bodyCache.remove(oldest.getKey()));
            }
        }
        bodyCache.put(key, new CachedBody(body, Instant.now()));
    }

    private record CachedBody(String body, Instant fetchedAt) {}
}
