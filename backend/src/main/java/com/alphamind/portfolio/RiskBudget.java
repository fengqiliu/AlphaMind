package com.alphamind.portfolio;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 按当前组合与拟买入金额推算的 Gate 5 数值。{@code complete=false} 时百分比为 null，
 * {@code problems} 说明原因（行情过期、取数失败、金额超过可用资金等），不用旧价格凑数。
 */
public record RiskBudget(
        String stockCode,
        String theme,
        double plannedBuyAmount,
        boolean complete,
        Double totalValue,
        Double projectedPositionPct,
        Double projectedThemePct,
        Double cashAfterTradePct,
        Double portfolioDrawdownPct,
        Double peakValue,
        List<Valuation> valuations,
        List<String> problems,
        OffsetDateTime computedAt) {

    /** 单条持仓的估值来源，冻结进报告用于说明数字从何而来。 */
    public record Valuation(String stockCode, String name, String theme, Double shares, Double price,
                            double marketValue, String source) {}
}
