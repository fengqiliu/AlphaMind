package com.alphamind.market.service;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketDataProvider;
import com.alphamind.market.provider.MarketQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MarketDataResolutionTest {
    @Test
    void providerChainUsesFirstAvailableQuoteAndHistoryAndKeepsProvenance() {
        MarketDataProvider unavailable = () -> "unavailable";
        MarketDataProvider available = new MarketDataProvider() {
            public String sourceName() { return "fixture"; }
            public Optional<MarketQuote> fetchQuote(String code, String hint) {
                return Optional.of(quote(code));
            }
            public Optional<List<DailyKline>> fetchDailyKlines(String code, int limit) {
                return Optional.of(List.of(bar(1), bar(2), bar(3)));
            }
        };

        MarketDataSnapshot snapshot = new MarketDataProviderChain(List.of(unavailable, available))
                .fetch("600519", "贵州茅台", 2);

        assertEquals("fixture", snapshot.quoteSource());
        assertEquals("fixture", snapshot.klineSource());
        assertEquals(2, snapshot.dailyKlines().size());
        assertEquals(LocalDate.of(2026, 8, 29), snapshot.dailyKlines().get(0).tradeDate());
    }

    @Test
    void providerChainFailsClearlyWhenNoCompleteRealSnapshotExists() {
        MarketDataProvider empty = () -> "empty";
        MarketDataProvider quoteOnly = new MarketDataProvider() {
            public String sourceName() { return "quote-only"; }
            public Optional<MarketQuote> fetchQuote(String c, String n) { return Optional.of(quote(c)); }
        };

        MarketDataUnavailableException failure = assertThrows(MarketDataUnavailableException.class,
                () -> new MarketDataProviderChain(List.of(empty, quoteOnly)).fetch("600519", "贵州茅台", 2));
        assertTrue(failure.getMessage().contains("真实"));
    }

    @Test
    void providerChainContinuesAfterOneProviderThrows() {
        MarketDataProvider broken = new MarketDataProvider() {
            public String sourceName() { return "broken"; }
            public Optional<MarketQuote> fetchQuote(String c, String n) {
                throw new IllegalStateException("upstream timeout");
            }
            public Optional<List<DailyKline>> fetchDailyKlines(String c, int limit) {
                throw new IllegalStateException("upstream timeout");
            }
        };

        MarketDataSnapshot snapshot = new MarketDataProviderChain(List.of(broken, provider("healthy")))
                .fetch("600519", "贵州茅台", 2);

        assertEquals("healthy", snapshot.quoteSource());
        assertEquals("healthy", snapshot.klineSource());
    }

    @Test
    void resilientServicePrefersFreshCacheThenFallsBackToLiveProvider() throws Exception {
        MarketDataCacheService cache = cache(30, 3600);
        MarketDataSnapshot cached = snapshot(Instant.now().minusSeconds(5));
        cache.put(cached);
        ResilientMarketDataService service = new ResilientMarketDataService(
                new MarketDataProviderChain(List.of(provider("live"))), cache);

        MarketDataResolution resolution = service.fetch("600519", "贵州茅台");
        assertEquals(MarketDataResolution.Status.FRESH_CACHE, resolution.status());
        assertEquals(cached, resolution.snapshot());
    }

    @Test
    void resilientServiceReturnsStaleCacheWithWarningWhenProviderFails() throws Exception {
        MarketDataCacheService cache = cache(1, 3600);
        MarketDataSnapshot cached = snapshot(Instant.now().minusSeconds(10));
        cache.put(cached);
        MarketDataProvider failing = () -> "failing";
        ResilientMarketDataService service = new ResilientMarketDataService(
                new MarketDataProviderChain(List.of(failing)), cache);

        MarketDataResolution resolution = service.fetch("600519", "贵州茅台");
        assertEquals(MarketDataResolution.Status.STALE_CACHE, resolution.status());
        assertNotNull(resolution.warning());
        assertTrue(resolution.warning().contains("最后成功缓存"));
    }

    private static MarketDataProvider provider(String source) {
        return new MarketDataProvider() {
            public String sourceName() { return source; }
            public Optional<MarketQuote> fetchQuote(String c, String n) { return Optional.of(quote(c)); }
            public Optional<List<DailyKline>> fetchDailyKlines(String c, int limit) {
                return Optional.of(List.of(bar(1), bar(2), bar(3)));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static MarketDataCacheService cache(long fresh, long stale) throws Exception {
        MarketDataCacheService cache = new MarketDataCacheService(
                mock(RedisTemplate.class), new ObjectMapper().findAndRegisterModules());
        set(cache, "freshTtlSeconds", fresh);
        set(cache, "staleMaxAgeSeconds", stale);
        return cache;
    }

    private static void set(Object target, String name, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static MarketDataSnapshot snapshot(Instant retrievedAt) {
        return new MarketDataSnapshot("600519", "贵州茅台", quote("600519"),
                List.of(bar(1), bar(2)), "fixture", "fixture", retrievedAt);
    }

    private static MarketQuote quote(String code) {
        return new MarketQuote(code, "贵州茅台", 10, 9, 10, 11, 9, 100, 1000, LocalDateTime.now());
    }

    private static DailyKline bar(int day) {
        return new DailyKline(LocalDate.of(2026, 8, 27 + day), 9, 10, 8, 11, 100, 1000, 1);
    }
}
