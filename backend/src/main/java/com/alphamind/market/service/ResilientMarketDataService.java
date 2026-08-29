package com.alphamind.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 行情读取顺序：新鲜缓存 → 真实 Provider 链 → 明示的过期缓存 → 失败。
 * 不在这里生成随机或默认行情，避免下游把伪数据当作实时数据使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientMarketDataService {

    private final MarketDataProviderChain providerChain;
    private final MarketDataCacheService cacheService;

    public MarketDataResolution fetch(String stockCode, String stockName) {
        return cacheService.getFresh(stockCode)
                .map(hit -> new MarketDataResolution(
                        hit.snapshot(), MarketDataResolution.Status.FRESH_CACHE,
                        hit.ageSeconds(), null))
                .orElseGet(() -> fetchProviderOrStale(stockCode, stockName));
    }

    private MarketDataResolution fetchProviderOrStale(String stockCode, String stockName) {
        try {
            MarketDataSnapshot snapshot = providerChain.fetch(stockCode, stockName);
            cacheService.put(snapshot);
            return new MarketDataResolution(snapshot, MarketDataResolution.Status.LIVE, 0, null);
        } catch (MarketDataUnavailableException providerFailure) {
            return cacheService.getStale(stockCode)
                    .map(hit -> {
                        String warning = "实时行情源不可用，当前展示最后成功缓存（"
                                + hit.ageSeconds() + " 秒前）";
                        log.warn("{}: stockCode={}, reason={}", warning, stockCode, providerFailure.getMessage());
                        return new MarketDataResolution(
                                hit.snapshot(), MarketDataResolution.Status.STALE_CACHE,
                                hit.ageSeconds(), warning);
                    })
                    .orElseThrow(() -> providerFailure);
        }
    }
}
