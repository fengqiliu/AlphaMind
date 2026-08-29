package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketDataProvider;
import com.alphamind.market.provider.MarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Assembles a complete snapshot from an ordered set of specialised providers.
 * A quote and history may come from different providers by design.
 */
@Slf4j
@Service
public class MarketDataProviderChain {

    public static final int DEFAULT_HISTORY_DAYS = 60;

    private final List<MarketDataProvider> providers;

    @Value("${alphamind.market.fetch-real-data:true}")
    private boolean fetchRealData = true;

    @Value("${alphamind.market.history-days:60}")
    private int historyDays = DEFAULT_HISTORY_DAYS;

    public MarketDataProviderChain(List<MarketDataProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public MarketDataSnapshot fetch(String stockCode, String stockNameHint) {
        return fetch(stockCode, stockNameHint, historyDays);
    }

    /**
     * Gets a live quote and at least {@code historyDays} daily bars. It never
     * manufactures placeholder data; callers can fall back to a stale cache on
     * {@link MarketDataUnavailableException}.
     */
    public MarketDataSnapshot fetch(String stockCode, String stockNameHint, int historyDays) {
        if (!fetchRealData) {
            throw new MarketDataUnavailableException("真实行情读取已由 FETCH_REAL_DATA=false 禁用");
        }
        if (historyDays <= 0) {
            throw new IllegalArgumentException("historyDays 必须大于 0");
        }

        ProviderValue<MarketQuote> quote = providers.stream()
                .map(provider -> new ProviderValue<>(provider.sourceName(),
                        safely(() -> provider.fetchQuote(stockCode, stockNameHint),
                                provider, stockCode, "实时报价")))
                .filter(ProviderValue::isPresent)
                .findFirst()
                .orElseThrow(() -> unavailable(stockCode, "实时行情"));

        ProviderValue<List<DailyKline>> history = providers.stream()
                .map(provider -> new ProviderValue<>(provider.sourceName(),
                        safely(() -> provider.fetchDailyKlines(stockCode, historyDays),
                                provider, stockCode, "日K线")))
                .filter(value -> value.value().filter(bars -> bars.size() >= historyDays).isPresent())
                .findFirst()
                .orElseThrow(() -> unavailable(stockCode, historyDays + " 日K线"));

        List<DailyKline> latestBars = history.value().orElseThrow().stream()
                .sorted(Comparator.comparing(DailyKline::tradeDate))
                .skip(Math.max(0, history.value().orElseThrow().size() - historyDays))
                .toList();
        String stockName = nonBlank(quote.value().orElseThrow().stockName())
                ? quote.value().orElseThrow().stockName()
                : nonBlank(stockNameHint) ? stockNameHint : stockCode;

        return new MarketDataSnapshot(
                stockCode,
                stockName,
                quote.value().orElseThrow(),
                latestBars,
                quote.sourceName(),
                history.sourceName(),
                Instant.now()
        );
    }

    private MarketDataUnavailableException unavailable(String stockCode, String dataKind) {
        log.warn("没有可用的真实{}: {}，已尝试 provider={}", dataKind, stockCode,
                providers.stream().map(MarketDataProvider::sourceName).toList());
        return new MarketDataUnavailableException("无法获取 " + stockCode + " 的真实" + dataKind);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private <T> Optional<T> safely(
            java.util.function.Supplier<Optional<T>> supplier,
            MarketDataProvider provider,
            String stockCode,
            String dataKind) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.warn("行情 Provider 调用失败，继续尝试下一来源: provider={}, stockCode={}, kind={}, reason={}",
                    provider.sourceName(), stockCode, dataKind, e.getMessage());
            return Optional.empty();
        }
    }

    private record ProviderValue<T>(String sourceName, Optional<T> value) {
        boolean isPresent() {
            return value.isPresent();
        }
    }
}
