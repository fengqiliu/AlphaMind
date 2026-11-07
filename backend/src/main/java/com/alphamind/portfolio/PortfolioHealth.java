package com.alphamind.portfolio;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 持仓体检结果：只提示需要复核的持仓，不给卖出价或减仓比例。
 * {@code totalValue} 为 null 表示有持仓缺当期行情，组合层面的权重检查未执行。
 */
public record PortfolioHealth(Double totalValue, List<Finding> findings, List<String> unavailable,
                              OffsetDateTime checkedAt) {

    public enum Kind { POSITION_LIMIT, THEME_LIMIT, CASH_FLOOR, DRAWDOWN, OVERHEATED, REVIEW_MISSING, REVIEW_EXPIRED }

    /** 一条触发项；value/limit 为百分比或 RSI 数值，证据类为 null。stockCode 为空表示组合级或主题级。 */
    public record Finding(Kind kind, String stockCode, String name, String message, Double value, Double limit) {}
}
