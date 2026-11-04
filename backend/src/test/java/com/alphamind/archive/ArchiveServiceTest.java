package com.alphamind.archive;

import com.alphamind.model.dto.MarketDataDTO;
import com.alphamind.model.dto.SentimentDataDTO;
import com.alphamind.news.provider.NewsArticle;
import com.alphamind.news.service.NewsSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveServiceTest {
    private final NewsArchiveRepository news = mock(NewsArchiveRepository.class);
    private final SentimentArchiveRepository sentiments = mock(SentimentArchiveRepository.class);
    private final ArchiveService service = new ArchiveService(news, sentiments);
    private static final Instant FETCHED = Instant.parse("2026-11-04T07:30:00Z");

    private static NewsSnapshot snapshot(NewsArticle... articles) {
        return new NewsSnapshot("600519", List.of(articles), "eastmoney", FETCHED);
    }

    @Test
    void savesOnlyArticlesNotYetArchivedWithOriginalFetchTime() {
        var known = new NewsArticle("旧标题", LocalDateTime.of(2026, 11, 4, 9, 0), "http://x/1");
        var fresh = new NewsArticle("新标题", LocalDateTime.of(2026, 11, 4, 10, 0), "http://x/2");
        when(news.existsByStockCodeAndDedupeKey("600519", "http://x/1")).thenReturn(true);
        when(news.existsByStockCodeAndDedupeKey("600519", "http://x/2")).thenReturn(false);

        int saved = service.archiveNews(snapshot(known, fresh));

        assertEquals(1, saved);
        verify(news).save(argThat(row -> "新标题".equals(row.getTitle())
                && "eastmoney".equals(row.getSource())
                && FETCHED.equals(row.getFetchedAt().toInstant())
                && "600519".equals(row.getStockCode())));
        verify(news, times(1)).save(any());
    }

    @Test
    void articlesWithoutUrlAreKeyedByTitleAndPublishTime() {
        var article = new NewsArticle("无链接", LocalDateTime.of(2026, 11, 4, 9, 0), null);

        service.archiveNews(snapshot(article));

        verify(news).existsByStockCodeAndDedupeKey("600519", "无链接|2026-11-04T09:00");
    }

    @Test
    void duplicateRaceIsIgnoredAndArchiveFailureNeverThrows() {
        when(news.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertEquals(0, service.archiveNews(snapshot(new NewsArticle("t", null, "http://x/1"))));

        when(news.existsByStockCodeAndDedupeKey(any(), any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> service.archiveNews(snapshot(new NewsArticle("t", null, "http://x/2"))));
    }

    @Test
    void emptyOrNullSnapshotIsNoOp() {
        assertEquals(0, service.archiveNews(null));
        assertEquals(0, service.archiveNews(snapshot()));
        verifyNoInteractions(news);
    }

    @Test
    void sentimentRowCapturesInputsScoreAndMethod() {
        var market = MarketDataDTO.builder().changePercent(1.5).turnoverRate(2.2).pe(18.0).updateTime("2026-11-04 15:00:00").build();
        var sentiment = SentimentDataDTO.builder().sentimentScore(0.62).signalCoverage(0.78).newsSignal(0.7)
                .newsDataSource("eastmoney").newsStale(false).valuationStatus("POSITIVE")
                .recentHeadlines(List.of("a", "b")).scoreExplanation("说明").build();

        service.archiveSentiment("600519", market, sentiment);

        verify(sentiments).save(argThat(row -> "600519".equals(row.getStockCode())
                && row.getSentimentScore() == 0.62 && row.getSignalCoverage() == 0.78
                && row.getNewsSignal() == 0.7 && row.getChangePercent() == 1.5
                && row.getPe() == 18.0 && row.getTurnoverRate() == 2.2
                && "POSITIVE".equals(row.getValuationStatus())
                && ArchiveService.SENTIMENT_METHOD.equals(row.getMethod())
                && "2026-11-04 15:00:00".equals(row.getMarketUpdateTime())
                && row.getHeadlineCount() == 2 && row.getComputedAt() != null));
    }

    @Test
    void sentimentArchiveFailureNeverThrowsAndNullsAreSkipped() {
        when(sentiments.save(any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> service.archiveSentiment("600519",
                MarketDataDTO.builder().build(), SentimentDataDTO.builder().sentimentScore(0.5).build()));
        service.archiveSentiment("600519", null, null);
        verify(sentiments, times(1)).save(any());
    }
}
