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

    /** 固定期望值：技术规则 V1 在该序列上的输出（重构时已核对与原实现逐行一致），任何评分改动都会让它失败。 */
    @Test
    void fixtureScoreIsPinned() {
        List<double[]> bars = fixture();
        var result = TechnicalScoring.score(bars, bars.get(bars.size() - 1)[1]);
        assertEquals(47, result.getTechnicalScore());
        assertEquals(java.util.Map.of("基础分", 50, "MACD柱", 15, "RSI12", -10, "KDJ_J", -8, "布林位置", 0, "限幅调整", 0),
                result.getScoreContributions());
        assertEquals(0.883, result.getMacd().getHistogram());
        assertEquals(73.32, result.getRsi().getRsi12());
        assertEquals(118.65, result.getKdj().getJ());
        assertEquals(52.54, result.getBollinger().getLower());
        assertEquals(62.64, result.getBollinger().getUpper());
    }

    @Test
    void agentDelegatesToTechnicalScoring() {
        List<double[]> bars = fixture();
        double price = bars.get(bars.size() - 1)[1];
        TechnicalAgent agent = new TechnicalAgent();
        try {
            agent.setContext("marketData", MarketDataDTO.builder().currentPrice(price).klines(bars).build());
            var fromAgent = agent.analyze(AnalysisReportDTO.builder().stockCode("TEST").build()).getTechnicalIndicators();
            assertEquals(TechnicalScoring.score(bars, price).getScoreContributions(), fromAgent.getScoreContributions());
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
