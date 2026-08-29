package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketQuote;

import java.time.Instant;
import java.util.List;

/**
 * Complete, provider-originated market snapshot. Cache implementations can store
 * this value without depending on a presentation DTO.
 */
public record MarketDataSnapshot(
        String stockCode,
        String stockName,
        MarketQuote quote,
        List<DailyKline> dailyKlines,
        String quoteSource,
        String klineSource,
        Instant retrievedAt
) {
    public MarketDataSnapshot {
        dailyKlines = List.copyOf(dailyKlines);
    }
}
