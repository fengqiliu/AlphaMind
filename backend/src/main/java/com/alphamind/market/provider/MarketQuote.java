package com.alphamind.market.provider;

import java.time.LocalDateTime;

/** A validated real-time A-share quote. Volume is expressed in shares. */
public record MarketQuote(
        String stockCode,
        String stockName,
        double open,
        double previousClose,
        double current,
        double high,
        double low,
        long volume,
        double amount,
        LocalDateTime quotedAt
) {
    public MarketQuote {
        if (current <= 0 || previousClose < 0 || open < 0 || high < 0 || low < 0 || volume < 0 || amount < 0) {
            throw new IllegalArgumentException("行情字段必须为非负值，当前价必须大于 0");
        }
    }
}
