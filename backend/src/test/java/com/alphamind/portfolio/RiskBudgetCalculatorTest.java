package com.alphamind.portfolio;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskBudgetCalculatorTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-06T10:00:00+08:00");
    static final String AI = "AI/电子链";

    static PortfolioProfile profile(Double peak, Holding... holdings) {
        return new PortfolioProfile(List.of(holdings), 20_000.0, 10_000.0, peak, NOW);
    }

    @Test
    void projectsPostTradeWeightsOnTheSameDenominator() {
        // 工业富联 1000 股 × 50 = 50,000；立讯 500 股 × 40 = 20,000（同主题）；场外基金 手工 0 主题不同 = 0
        var p = profile(110_000.0,
                new Holding("601138", "工业富联", AI, 1000.0, null),
                new Holding("002475", "立讯精密", AI, 500.0, null),
                new Holding(null, "某债基", "债券", null, 0.0));
        var quotes = Map.of("601138", RiskBudgetCalculator.Quote.of(50, "LIVE"),
                "002475", RiskBudgetCalculator.Quote.of(40, "FRESH_CACHE"));

        var b = RiskBudgetCalculator.compute(p, "601138", AI, 5_000, quotes, NOW);

        assertTrue(b.complete(), b.problems().toString());
        assertEquals(100_000.0, b.totalValue());           // 70,000 持仓 + 30,000 现金/低波动
        assertEquals(55.0, b.projectedPositionPct());       // (50,000 + 5,000) / 100,000
        assertEquals(75.0, b.projectedThemePct());          // (70,000 + 5,000) / 100,000
        assertEquals(25.0, b.cashAfterTradePct());          // (30,000 − 5,000) / 100,000
        assertEquals(9.09, b.portfolioDrawdownPct());       // (110,000 − 100,000) / 110,000
        assertEquals(3, b.valuations().size());
    }

    @Test
    void newHighRaisesPeakAndGivesZeroDrawdown() {
        var b = RiskBudgetCalculator.compute(profile(20_000.0), "600036", "银行", 0, Map.of(), NOW);
        assertEquals(30_000.0, b.peakValue());
        assertEquals(0.0, b.portfolioDrawdownPct());
    }

    @Test
    void anyStaleHoldingWithholdsEveryPercentage() {
        var p = profile(100_000.0, new Holding("601138", "工业富联", AI, 1000.0, null));
        var b = RiskBudgetCalculator.compute(p, "601138", AI, 0,
                Map.of("601138", RiskBudgetCalculator.Quote.failed("实时行情不可用，只有过期缓存")), NOW);

        assertFalse(b.complete());
        assertNull(b.totalValue());
        assertNull(b.projectedPositionPct());
        assertEquals(List.of("工业富联（601138）：实时行情不可用，只有过期缓存"), b.problems());
    }

    @Test
    void buyingMoreThanAvailableCashIsRejected() {
        var b = RiskBudgetCalculator.compute(profile(null), "600036", "银行", 30_001, Map.of(), NOW);
        assertFalse(b.complete());
        assertTrue(b.problems().contains("拟买入金额超过现金与低波动资产合计"));
    }

    @Test
    void missingPeakLeavesDrawdownForManualEntry() {
        var b = RiskBudgetCalculator.compute(profile(null), "600036", "银行", 1_000, Map.of(), NOW);
        assertFalse(b.complete());
        assertNull(b.portfolioDrawdownPct());
        assertEquals(3.33, b.projectedPositionPct());
        assertEquals(List.of("未填写组合历史高点，回撤需手动填写"), b.problems());
    }
}
