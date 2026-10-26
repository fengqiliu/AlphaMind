package com.alphamind.scoring;

import com.alphamind.agent.TechnicalAgent;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.MarketDataDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalScoringTest {

    private static List<double[]> flat(int n) {
        List<double[]> bars = new ArrayList<>();
        for (int i = 0; i < n; i++) bars.add(new double[]{100, 100, 100, 100});
        return bars;
    }

    /** 正弦叠加趋势，使 MACD/RSI/KDJ/布林都出现非零贡献。 */
    private static List<double[]> fixture() {
        List<double[]> bars = new ArrayList<>();
        double prev = 50;
        for (int i = 0; i < 60; i++) {
            double close = 50 + i * 0.15 + 4 * Math.sin(i / 3.0);
            bars.add(new double[]{prev, close, Math.min(prev, close) - 0.4, Math.max(prev, close) + 0.4});
            prev = close;
        }
        return bars;
    }

    @Test
    void flatSeriesScoresFifty() {
        var result = TechnicalScoring.score(flat(40), 100);
        assertEquals(50, result.getTechnicalScore());
        assertEquals(50, result.getRsi().getRsi12());
        assertEquals(0, result.getMacd().getHistogram());
        assertEquals(40, result.getSampleSize());
    }

    @Test
    void matchesAgentOutputOnFixture() {
        List<double[]> bars = fixture();
        double price = bars.get(bars.size() - 1)[1];
        TechnicalAgent agent = new TechnicalAgent();
        try {
            agent.setContext("marketData", MarketDataDTO.builder().currentPrice(price).klines(bars).build());
            var fromAgent = agent.analyze(AnalysisReportDTO.builder().stockCode("TEST").build()).getTechnicalIndicators();
            var pure = TechnicalScoring.score(bars, price);

            assertEquals(fromAgent.getTechnicalScore(), pure.getTechnicalScore());
            assertEquals(fromAgent.getScoreContributions(), pure.getScoreContributions());
            assertEquals(fromAgent.getScoreExplanation(), pure.getScoreExplanation());
            assertEquals(fromAgent.getMacd(), pure.getMacd());
            assertEquals(fromAgent.getRsi(), pure.getRsi());
            assertEquals(fromAgent.getKdj(), pure.getKdj());
            assertEquals(fromAgent.getBollinger(), pure.getBollinger());
            assertNull(pure.getAiInterpretation(), "纯函数不调用 LLM");
        } finally {
            agent.clearContext();
        }
    }

    @Test
    void explanationDisclosesBacktestFinding() {
        String explanation = TechnicalScoring.score(flat(40), 100).getScoreExplanation();
        assertTrue(explanation.contains(TechnicalScoring.BACKTEST_FINDING));
        assertTrue(explanation.contains("未见区分力"));
        assertFalse(explanation.contains("未经回测校准"), "技术分已回测，旧说法不再成立");
    }

    @Test
    void rejectsShortSeries() {
        var error = assertThrows(IllegalArgumentException.class, () -> TechnicalScoring.score(flat(33), 100));
        assertTrue(error.getMessage().contains("至少需要34根"));
    }
}
