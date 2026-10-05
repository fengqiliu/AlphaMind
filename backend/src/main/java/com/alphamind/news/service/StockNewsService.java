package com.alphamind.news.service;

import com.alphamind.news.provider.EastmoneyNewsProvider;
import com.alphamind.news.provider.NewsArticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 新闻读取顺序：新鲜缓存 → 真实 Provider → 明示的过期缓存 → empty。
 * 不在这里编造新闻标题或数量，避免下游把伪数据当作真实舆情使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsService {

    private static final int DEFAULT_LIMIT = 20;

    private final EastmoneyNewsProvider newsProvider;
    private final NewsCacheService cacheService;

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

    private Optional<NewsResolution> fetchProviderOrStale(String stockCode, int limit) {
        Optional<List<NewsArticle>> fetched = newsProvider.fetchNews(stockCode, limit);
        if (fetched.isPresent()) {
            NewsSnapshot snapshot = new NewsSnapshot(
                    stockCode, fetched.get(), newsProvider.sourceName(), Instant.now());
            cacheService.put(snapshot);
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
}
