package com.alphamind.market.service;

/** 带降级状态的报价读取结果。语义与 {@link MarketDataResolution} 一致，但不含 K 线。 */
public record QuoteResolution(
        QuoteSnapshot snapshot,
        MarketDataResolution.Status status,
        long cacheAgeSeconds
) {
    /** 是否为过期缓存——调用方据此给前端打降级标记。 */
    public boolean stale() {
        return status == MarketDataResolution.Status.STALE_CACHE;
    }

    public String sourceName() {
        return snapshot.sourceName();
    }
}
