package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.news.provider.NewsArticle;
import com.alphamind.news.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SentimentAgentTest {
    private final StockNewsService news = mock(StockNewsService.class);
    private final SentimentAgent agent = new SentimentAgent(news);

    @AfterEach
    void clearContext() { agent.clearContext(); }

    private MarketDataDTO market(Double pe) {
        return MarketDataDTO.builder().stockName("测试标的").changePercent(0.0).turnoverRate(2.0)
                .pe(pe).klineVolumes(Collections.nCopies(20, 100L)).build();
    }

    private SentimentDataDTO analyze(MarketDataDTO market) {
        agent.setContext("marketData", market);
        return agent.analyze(AnalysisReportDTO.builder().stockCode("600519").build()).getSentimentData();
    }

    @Test
    void missingPeDoesNotReceiveLossPenaltyAndWeightsAreRenormalized() {
        var missing = analyze(market(null));
        // (0.5*0.4 + 0.566*0.2 + (0.55+0.2/0.7*0.15)*0.15) / 0.75 = 0.54
        assertEquals(0.54, missing.getSentimentScore());
        assertEquals("MISSING", missing.getValuationStatus());
        assertEquals(0.66, missing.getSignalCoverage());
        assertTrue(missing.getScoreExplanation().contains("涨跌幅 53.3%"));
        assertFalse(missing.getScoreExplanation().contains("PE 25.0%"));
        assertTrue(missing.getAnalysisSummary().contains("未据此推断亏损"));
        var negative = analyze(market(-10.0));
        assertEquals("NON_POSITIVE", negative.getValuationStatus());
        assertTrue(negative.getSentimentScore() < missing.getSentimentScore());
        assertTrue(negative.getNegativeFactors().stream().anyMatch(value -> value.contains("PE为负值")));
    }

    @Test
    void zeroAndNonFinitePeAreUnavailableRatherThanLossEvidence() {
        var baseline = analyze(market(null));
        for (double pe : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var actual = analyze(market(pe));
            assertEquals("MISSING", actual.getValuationStatus());
            assertEquals(baseline.getSentimentScore(), actual.getSentimentScore());
        }
    }

    @Test
    void positivePeBoundariesRetainHeuristicBandsWithoutHistoricalClaims() {
        for (double pe : new double[] {14.99, 15, 35, 35.01, 50, 50.01}) {
            var actual = analyze(market(pe));
            assertEquals("POSITIVE", actual.getValuationStatus());
            double peSignal = pe < 15 ? 0.72 : pe <= 35 ? 0.60 : pe <= 50 ? 0.48 : 0.32;
            double expected = Math.round((0.5 * 0.40 + peSignal * 0.25 + 0.566 * 0.20
                    + (0.55 + 0.2 / 0.7 * 0.15) * 0.15) * 100) / 100.0;
            assertEquals(expected, actual.getSentimentScore());
            assertFalse(actual.getPositiveFactors().stream().anyMatch(value -> value.contains("历史低位") || value.contains("安全边际充足")));
        }
    }

    @Test
    void marketCapDoesNotInventMediaOrInstitutionalAttention() {
        var missing = analyze(market(null));
        var large = market(null);
        large.setMarketCap(900_000_000_000L);
        var actual = analyze(large);
        assertNull(missing.getMediaAttention());
        assertNull(actual.getMediaAttention());
        assertEquals(missing.getSentimentScore(), actual.getSentimentScore());
        assertTrue(actual.getDataGaps().stream().anyMatch(value -> value.contains("无媒体关注度实测来源")));
        assertFalse(actual.getPositiveFactors().stream().anyMatch(value -> value.contains("机构配置")));
        assertNotNull(agent.chat(ChatMessage.builder().content("解释依据").build()).getContent());
    }

    @Test
    void invalidOrInsufficientVolumesAreExcludedWithoutNaN() {
        List<List<Long>> samples = new ArrayList<>();
        samples.add(List.of());
        samples.add(Collections.nCopies(19, 100L));
        samples.add(Collections.nCopies(20, 0L));
        samples.add(Collections.nCopies(20, -1L));
        samples.add(Collections.nCopies(20, null));
        for (var volumes : samples) {
            var market = market(null);
            market.setKlineVolumes(volumes);
            var actual = analyze(market);
            assertEquals(0.52, actual.getSentimentScore());
            assertTrue(actual.getDataGaps().stream().anyMatch(value -> value.contains("近20日成交量")));
            assertFalse(actual.getScoreExplanation().contains("量能 "));
        }
    }

    @Test
    void noAvailableMarketSignalsAreExplicitlyMarkedAsPlaceholder() {
        var actual = analyze(MarketDataDTO.builder().build());
        assertEquals(0.5, actual.getSentimentScore());
        assertTrue(actual.getScoreExplanation().contains("中性占位"));
        assertEquals("数据不足", actual.getSentimentTrend());
        assertEquals(0.0, actual.getSignalCoverage());
        assertTrue(actual.getPositiveFactors().stream().anyMatch(value -> value.contains("不代表风险已排除")));
    }

    @Test
    void newsAloneUsesItsActualSignalAndEmptyNewsDoesNotCreateEvidence() {
        when(news.news("600519")).thenReturn(Optional.of(new NewsResolution(
                new NewsSnapshot("600519", List.of(new NewsArticle("公司回购股份", null, null)), "TEST", Instant.now()),
                NewsResolution.Status.LIVE, 0, null)));
        var actual = analyze(MarketDataDTO.builder().build());
        assertEquals(Math.round(actual.getNewsSignal() * 100) / 100.0, actual.getSentimentScore());
        assertTrue(actual.getScoreExplanation().contains("新闻标题关键词 100.0%"));
        when(news.news("600519")).thenReturn(Optional.of(new NewsResolution(
                new NewsSnapshot("600519", List.of(), "TEST", Instant.now()), NewsResolution.Status.LIVE, 0, null)));
        var empty = analyze(MarketDataDTO.builder().build());
        assertNull(empty.getNewsSignal());
        assertNull(empty.getNewsCountBySource());
        assertEquals("数据不足", empty.getSentimentTrend());
    }

    @Test
    void newsRetainsItsTwelvePercentShareAndExplainsWeights() {
        when(news.news("600519")).thenReturn(Optional.of(new NewsResolution(
                new NewsSnapshot("600519", List.of(new NewsArticle("公司回购股份", null, null)), "TEST", Instant.now()),
                NewsResolution.Status.LIVE, 0, null)));
        var actual = analyze(market(null));
        double rawMarket = (0.5 * 0.4 + 0.566 * 0.2 + (0.55 + 0.2 / 0.7 * 0.15) * 0.15) / 0.75;
        assertEquals(Math.round((rawMarket * 0.88 + actual.getNewsSignal() * 0.12) * 100) / 100.0,
                actual.getSentimentScore());
        assertTrue(actual.getScoreExplanation().contains("新闻标题关键词 12.0%"));
        assertEquals(0.78, actual.getSignalCoverage());
        assertNull(actual.getMediaAttention());
        assertEquals(List.of("公司回购股份"), actual.getRecentHeadlines());
    }
}
