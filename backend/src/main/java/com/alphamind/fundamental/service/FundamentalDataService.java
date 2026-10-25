package com.alphamind.fundamental.service;

import com.alphamind.fundamental.provider.EastmoneyFundamentalProvider;
import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 财报/估值读取顺序：新鲜缓存 → 真实 Provider → 明示的过期缓存 → empty。
 * 不在这里补默认 PE/PB 或推测财务数据，避免下游把缺失当作“估值合理”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalDataService {

    private final EastmoneyFundamentalProvider provider;
    private final FundamentalCacheService cacheService;

    /** 读取财报与估值；数据源与缓存都不可用（或证券无财报，如 ETF）时返回 empty，绝不抛出。 */
    public Optional<FundamentalResolution> fundamentals(String stockCode) {
        return cacheService.getFresh(stockCode)
                .map(hit -> new FundamentalResolution(hit.snapshot(), FundamentalResolution.Status.FRESH_CACHE,
                        hit.ageSeconds(), null))
                .or(() -> fetchProviderOrStale(stockCode));
    }

    private Optional<FundamentalResolution> fetchProviderOrStale(String stockCode) {
        ValuationMetrics valuation = provider.fetchValuation(stockCode).orElse(null);
        FinancialMetrics financials = provider.fetchFinancials(stockCode).orElse(null);
        if (valuation != null || financials != null) {
            FundamentalSnapshot snapshot = new FundamentalSnapshot(stockCode, valuation, financials,
                    provider.sourceName(), Instant.now());
            cacheService.put(snapshot);
            return Optional.of(new FundamentalResolution(snapshot, FundamentalResolution.Status.LIVE, 0, null));
        }
        return cacheService.getStale(stockCode).map(hit -> {
            String warning = "财报/估值数据源不可用，当前展示最后成功缓存（" + hit.ageSeconds() + " 秒前）";
            log.warn("{}: stockCode={}", warning, stockCode);
            return new FundamentalResolution(hit.snapshot(), FundamentalResolution.Status.STALE_CACHE,
                    hit.ageSeconds(), warning);
        });
    }
}
