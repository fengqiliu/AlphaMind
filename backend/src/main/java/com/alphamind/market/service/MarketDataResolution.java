package com.alphamind.market.service;

/** 带降级状态的行情读取结果。 */
public record MarketDataResolution(
        MarketDataSnapshot snapshot,
        Status status,
        long cacheAgeSeconds,
        String warning
) {
    public enum Status {
        LIVE,
        FRESH_CACHE,
        STALE_CACHE
    }
}
