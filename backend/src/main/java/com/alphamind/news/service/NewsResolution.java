package com.alphamind.news.service;

/**
 * 带降级状态的新闻读取结果。状态语义与行情 {@link com.alphamind.market.service.MarketDataResolution} 一致：
 * LIVE 直连成功，FRESH_CACHE 新鲜缓存，STALE_CACHE 过期缓存（上游失败时的最后成功数据）。
 */
public record NewsResolution(NewsSnapshot snapshot, Status status, long cacheAgeSeconds, String warning) {

    public enum Status {
        LIVE,
        FRESH_CACHE,
        STALE_CACHE
    }

    /** 是否为过期缓存——调用方据此给前端打降级标记。 */
    public boolean stale() {
        return status == Status.STALE_CACHE;
    }
}
