package com.alphamind.market.provider;

import java.time.LocalDate;

/** One unadjusted/adjusted daily bar supplied by a market-data provider. */
public record DailyKline(
        LocalDate tradeDate,
        double open,
        double close,
        double low,
        double high,
        long volume,
        double amount,
        double turnoverRate
) {
    public DailyKline {
        if (tradeDate == null || open <= 0 || close <= 0 || low <= 0 || high <= 0
                || volume < 0 || amount < 0 || turnoverRate < 0
                || high < low || high < Math.max(open, close) || low > Math.min(open, close)) {
            throw new IllegalArgumentException("日K线包含无效字段");
        }
    }
}
