package com.alphamind.fundamental.service;

/**
 * 带降级状态的财报/估值读取结果，状态语义与行情、新闻一致：
 * LIVE 直连成功，FRESH_CACHE 新鲜缓存，STALE_CACHE 过期缓存（上游失败时的最后成功数据）。
 */
public record FundamentalResolution(FundamentalSnapshot snapshot, Status status, long cacheAgeSeconds, String warning) {

    public enum Status {
        LIVE,
        FRESH_CACHE,
        STALE_CACHE
    }

    public boolean stale() {
        return status == Status.STALE_CACHE;
    }
}
