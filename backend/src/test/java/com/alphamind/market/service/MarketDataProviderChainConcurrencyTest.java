package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketDataProvider;
import com.alphamind.market.provider.MarketQuote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataProviderChainConcurrencyTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static MarketQuote quote(String code) {
        return new MarketQuote(code, "贵州茅台", 10, 9, 10, 11, 9, 100, 1000, LocalDateTime.now());
    }

    private static DailyKline bar(int day) {
        return new DailyKline(LocalDate.of(2026, 8, 27 + day), 9, 10, 8, 11, 100, 1000, 1);
    }

    /** 报价请求只有在 K 线请求已经开始后才返回：顺序执行时会等待超时而拿不到报价。 */
    @Test
    void fetchesQuoteAndHistoryConcurrently() {
        CountDownLatch historyStarted = new CountDownLatch(1);
        MarketDataProvider quotes = new MarketDataProvider() {
            public String sourceName() { return "quotes"; }
            public Optional<MarketQuote> fetchQuote(String code, String hint) {
                try {
                    return historyStarted.await(2, TimeUnit.SECONDS) ? Optional.of(quote(code)) : Optional.empty();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        };
        MarketDataProvider history = new MarketDataProvider() {
            public String sourceName() { return "history"; }
            public Optional<List<DailyKline>> fetchDailyKlines(String code, int limit) {
                historyStarted.countDown();
                return Optional.of(List.of(bar(1), bar(2), bar(3)));
            }
        };

        MarketDataSnapshot snapshot = new MarketDataProviderChain(List.of(quotes, history), executor)
                .fetch("600519", "贵州茅台", 2);

        assertEquals("quotes", snapshot.quoteSource());
        assertEquals("history", snapshot.klineSource());
    }

    @Test
    void missingQuoteStillFailsWithUnavailableException() {
        MarketDataProvider historyOnly = new MarketDataProvider() {
            public String sourceName() { return "history"; }
            public Optional<List<DailyKline>> fetchDailyKlines(String code, int limit) {
                return Optional.of(List.of(bar(1), bar(2), bar(3)));
            }
        };
        var chain = new MarketDataProviderChain(List.of(historyOnly), executor);
        var error = assertThrows(MarketDataUnavailableException.class, () -> chain.fetch("600519", "贵州茅台", 2));
        assertTrue(error.getMessage().contains("实时行情"));
    }
}
