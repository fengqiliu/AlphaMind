package com.alphamind.market.service;

import com.alphamind.market.provider.BatchQuoteProvider;
import com.alphamind.market.provider.MarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 只读报价通道：新鲜缓存 → 批量真实 Provider → 明示的过期缓存 → 缺席。
 *
 * <p>为什么不复用 {@link ResilientMarketDataService}：那条链要求报价**和** 60 根日 K 线同时可用，
 * 缺一即抛异常。股票搜索、自选股列表、周荐评分只需要价格，为一个价格去拉整段 K 线既浪费，
 * 又会在 K 线源故障时把本来可用的报价一起拖垮。
 *
 * <p>与整个 market 包一致：拿不到就缺席，绝不用默认值或随机数填充。调用方必须能区分
 * "这只股票没有价格"和"这只股票价格为 0"。
 */
@Slf4j
@Service
public class StockQuoteService {

    private final List<BatchQuoteProvider> batchProviders;
    private final MarketDataCacheService cacheService;

    @Value("${alphamind.market.fetch-real-data:true}")
    private boolean fetchRealData = true;

    public StockQuoteService(List<BatchQuoteProvider> batchProviders, MarketDataCacheService cacheService) {
        this.batchProviders = List.copyOf(batchProviders);
        this.cacheService = cacheService;
    }

    public Optional<QuoteResolution> quote(String stockCode) {
        return Optional.ofNullable(quotes(List.of(stockCode)).get(stockCode));
    }

    /**
     * 批量读取报价。
     *
     * @return 代码 → 读取结果；**只包含真正拿到数据的股票**，其余代码直接缺席
     */
    public Map<String, QuoteResolution> quotes(Collection<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, QuoteResolution> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (String stockCode : stockCodes.stream().distinct().toList()) {
            Optional<MarketDataCacheService.QuoteCacheHit> fresh = cacheService.getFreshQuote(stockCode);
            if (fresh.isPresent()) {
                resolved.put(stockCode, new QuoteResolution(
                        fresh.get().snapshot(), MarketDataResolution.Status.FRESH_CACHE,
                        fresh.get().ageSeconds()));
            } else {
                missing.add(stockCode);
            }
        }

        if (!missing.isEmpty()) {
            resolved.putAll(fetchAndCache(missing));
        }

        // 仍然缺席的，退到明示的过期缓存；再拿不到就让它缺席。
        for (String stockCode : missing) {
            if (resolved.containsKey(stockCode)) {
                continue;
            }
            cacheService.getStaleQuote(stockCode).ifPresent(hit -> resolved.put(stockCode, new QuoteResolution(
                    hit.snapshot(), MarketDataResolution.Status.STALE_CACHE, hit.ageSeconds())));
        }

        return resolved;
    }

    private Map<String, QuoteResolution> fetchAndCache(List<String> stockCodes) {
        if (!fetchRealData) {
            log.debug("真实行情读取已由 FETCH_REAL_DATA=false 禁用，仅允许缓存命中");
            return Map.of();
        }

        Map<String, QuoteResolution> live = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>(stockCodes);

        for (BatchQuoteProvider provider : batchProviders) {
            if (pending.isEmpty()) {
                break;
            }
            Map<String, MarketQuote> quotes;
            try {
                quotes = provider.fetchQuotes(pending);
            } catch (RuntimeException e) {
                log.warn("批量报价 Provider 调用失败，继续尝试下一来源: provider={}, reason={}",
                        provider.sourceName(), e.getMessage());
                continue;
            }

            Instant retrievedAt = Instant.now();
            quotes.forEach((stockCode, quote) -> {
                QuoteSnapshot snapshot = new QuoteSnapshot(quote, provider.sourceName(), retrievedAt);
                cacheService.putQuote(snapshot);
                live.put(stockCode, new QuoteResolution(snapshot, MarketDataResolution.Status.LIVE, 0));
            });
            pending.removeAll(quotes.keySet());
        }

        if (!pending.isEmpty()) {
            log.warn("没有可用的真实报价: {} 只股票未获取到，已尝试 provider={}",
                    pending.size(), batchProviders.stream().map(BatchQuoteProvider::sourceName).toList());
        }
        return live;
    }
}
