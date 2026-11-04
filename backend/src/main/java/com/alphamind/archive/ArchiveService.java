package com.alphamind.archive;

import com.alphamind.model.dto.MarketDataDTO;
import com.alphamind.model.dto.SentimentDataDTO;
import com.alphamind.news.provider.NewsArticle;
import com.alphamind.news.service.NewsSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 只增不改的新闻与舆情输入存档，为日后时间对齐的舆情回测积累历史数据。
 * 存档是旁路：任何失败只记日志，绝不影响分析主流程。
 */
@Slf4j
@Service
public class ArchiveService {

    /** 舆情评分口径版本；评分规则改变时同步修改，回测据此区分新旧分数。 */
    public static final String SENTIMENT_METHOD = "SENTIMENT_V2_HEADLINES";

    private final NewsArchiveRepository newsRepository;
    private final SentimentArchiveRepository sentimentRepository;

    public ArchiveService(NewsArchiveRepository newsRepository, SentimentArchiveRepository sentimentRepository) {
        this.newsRepository = newsRepository;
        this.sentimentRepository = sentimentRepository;
    }

    /**
     * 存档快照中尚未存过的新闻，抓取时间取快照的真实抓取时刻（过期缓存重放不会伪造成“刚抓到”）。
     * 返回新写入的条数。
     */
    public int archiveNews(NewsSnapshot snapshot) {
        if (snapshot == null || snapshot.articles() == null || snapshot.articles().isEmpty()) {
            return 0;
        }
        int saved = 0;
        OffsetDateTime fetchedAt = snapshot.retrievedAt().atOffset(ZoneOffset.UTC);
        for (NewsArticle article : snapshot.articles()) {
            if (article == null || article.title() == null) {
                continue;
            }
            try {
                String key = dedupeKey(article);
                if (newsRepository.existsByStockCodeAndDedupeKey(snapshot.stockCode(), key)) {
                    continue;
                }
                newsRepository.save(NewsArchiveEntity.builder()
                        .stockCode(snapshot.stockCode())
                        .title(article.title())
                        .publishedAt(article.publishedAt())
                        .url(article.url())
                        .source(snapshot.sourceName())
                        .fetchedAt(fetchedAt)
                        .dedupeKey(key)
                        .build());
                saved++;
            } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
                // 并发写入同一条新闻，唯一约束已保证只留一行
            } catch (RuntimeException e) {
                log.warn("新闻存档失败（非致命）: stockCode={}, reason={}", snapshot.stockCode(), e.getMessage());
            }
        }
        return saved;
    }

    /** 存档一次舆情计算的输入与结果。 */
    public void archiveSentiment(String stockCode, MarketDataDTO market, SentimentDataDTO sentiment) {
        if (stockCode == null || sentiment == null) {
            return;
        }
        try {
            List<String> headlines = sentiment.getRecentHeadlines();
            sentimentRepository.save(SentimentArchiveEntity.builder()
                    .stockCode(stockCode)
                    .computedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .method(SENTIMENT_METHOD)
                    .sentimentScore(sentiment.getSentimentScore())
                    .signalCoverage(sentiment.getSignalCoverage())
                    .newsSignal(sentiment.getNewsSignal())
                    .newsDataSource(sentiment.getNewsDataSource())
                    .newsStale(sentiment.getNewsStale())
                    .headlineCount(headlines == null ? 0 : headlines.size())
                    .changePercent(market == null ? null : market.getChangePercent())
                    .pe(market == null ? null : market.getPe())
                    .turnoverRate(market == null ? null : market.getTurnoverRate())
                    .valuationStatus(sentiment.getValuationStatus())
                    .marketUpdateTime(market == null ? null : market.getUpdateTime())
                    .scoreExplanation(sentiment.getScoreExplanation())
                    .build());
        } catch (RuntimeException e) {
            log.warn("舆情存档失败（非致命）: stockCode={}, reason={}", stockCode, e.getMessage());
        }
    }

    /** 有链接按链接去重；没有链接时用标题 + 发布时间。 */
    static String dedupeKey(NewsArticle article) {
        if (article.url() != null && !article.url().isBlank()) {
            return article.url();
        }
        return article.title() + "|" + (article.publishedAt() == null ? "" : article.publishedAt());
    }
}
