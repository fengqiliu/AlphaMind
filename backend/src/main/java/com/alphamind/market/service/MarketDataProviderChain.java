package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketDataProvider;
import com.alphamind.market.provider.MarketQuote;
import com.alphamind.config.ConcurrencyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Assembles a complete snapshot from an ordered set of specialised providers.
 * A quote and history may come from different providers by design.
 */
@Slf4j
@Service
public class MarketDataProviderChain {

    public static final int DEFAULT_HISTORY_DAYS = 60;

    private final List<MarketDataProvider> providers;
    private final Executor executor;

    @Value("${alphamind.market.fetch-real-data:true}")
    private boolean fetchRealData = true;

    @Value("${alphamind.market.history-days:60}")
    private int historyDays = DEFAULT_HISTORY_DAYS;

    public MarketDataProviderChain(List<MarketDataProvider> providers) {
        this(providers, Runnable::run);
    }

    @Autowired
    public MarketDataProviderChain(List<MarketDataProvider> providers, ConcurrencyConfig.IoExecutor io) {
        this(providers, io.executor());
    }

    public MarketDataProviderChain(List<MarketDataProvider> providers, Executor executor) {
        this.providers = List.copyOf(providers);
        this.executor = executor;
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

        // 报价与日 K 互不依赖，并发请求；行情阶段耗时约为两者中较慢的一个
        CompletableFuture<ProviderValue<MarketQuote>> quoteFuture = CompletableFuture.supplyAsync(() -> providers.stream()
                .map(provider -> new ProviderValue<>(provider.sourceName(),
                        safely(() -> provider.fetchQuote(stockCode, stockNameHint),
                                provider, stockCode, "实时报价")))
                .filter(ProviderValue::isPresent)
                .findFirst()
                .orElseThrow(() -> unavailable(stockCode, "实时行情")), executor);
        CompletableFuture<ProviderValue<List<DailyKline>>> historyFuture = CompletableFuture.supplyAsync(() -> providers.stream()
                .map(provider -> new ProviderValue<>(provider.sourceName(),
                        safely(() -> provider.fetchDailyKlines(stockCode, historyDays),
                                provider, stockCode, "日K线")))
                .filter(value -> value.value().filter(bars -> bars.size() >= historyDays).isPresent())
                .findFirst()
                .orElseThrow(() -> unavailable(stockCode, historyDays + " 日K线")), executor);
        ProviderValue<MarketQuote> quote = join(quoteFuture);
        ProviderValue<List<DailyKline>> history = join(historyFuture);

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

    /** 解开 CompletionException，保持调用方看到的异常类型与顺序执行时一致。 */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
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
