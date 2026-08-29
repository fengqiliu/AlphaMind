package com.alphamind.market.provider;

import com.alphamind.market.service.MarketDataSnapshot;
import com.alphamind.market.service.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline contract tests for provider-facing value objects.
 *
 * These tests intentionally do not call a live market-data endpoint.  They
 * protect the boundary that makes it possible to distinguish a validated
 * upstream value from synthetic fallback data.
 */
class MarketProviderContractTest {

    @Test
    void unavailableDataHasAnActionableExceptionType() {
        MarketDataUnavailableException exception = new MarketDataUnavailableException(
                "600519: no provider returned a usable snapshot");

        assertEquals("600519: no provider returned a usable snapshot", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void quoteRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> quote(0, 10, 10, 11, 9, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> quote(10, -1, 10, 11, 9, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> quote(10, 10, -1, 11, 9, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> quote(10, 10, 10, 11, 9, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> quote(10, 10, 10, 11, 9, 1, -1));
    }

    @Test
    void klineRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyKline(null, 1, 1, 1, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DailyKline(LocalDate.now(), 0, 1, 1, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DailyKline(LocalDate.now(), 1, 1, 1, 1, -1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DailyKline(LocalDate.now(), 10, 11, 9, 10, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DailyKline(LocalDate.now(), 10, 10, 9, 11, 1, 1, -0.1));
    }

    @Test
    void providerDefaultMethodsRepresentUnavailableData() {
        MarketDataProvider provider = () -> "fixture";

        assertEquals("fixture", provider.sourceName());
        assertEquals(Optional.empty(), provider.fetchQuote("600519", "贵州茅台"));
        assertEquals(Optional.empty(), provider.fetchDailyKlines("600519", 60));
    }

    @Test
    void snapshotCopiesKlinesAndPreservesProvenance() {
        List<DailyKline> source = new ArrayList<>();
        source.add(kline());
        MarketDataSnapshot snapshot = new MarketDataSnapshot(
                "600519", "贵州茅台", quote(10, 9, 10, 11, 9, 100, 1000),
                source, "sina", "fixture", Instant.now());

        source.clear();
        assertEquals(1, snapshot.dailyKlines().size());
        assertEquals("sina", snapshot.quoteSource());
        assertEquals("fixture", snapshot.klineSource());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.dailyKlines().clear());
    }

    @Test
    void snapshotSupportsExplicitJsonCacheRoundTrip() throws Exception {
        MarketDataSnapshot source = new MarketDataSnapshot(
                "600519", "贵州茅台", quote(10, 9, 10, 11, 9, 100, 1000),
                List.of(kline()), "sina", "eastmoney", Instant.parse("2026-08-30T00:00:00Z"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        MarketDataSnapshot restored = mapper.readValue(
                mapper.writeValueAsString(source), MarketDataSnapshot.class);

        assertEquals(source, restored);
    }

    private static MarketQuote quote(double current, double previousClose, double open,
                                     double high, double low, long volume, double amount) {
        return new MarketQuote("600519", "贵州茅台", open, previousClose, current,
                high, low, volume, amount, Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    }

    private static DailyKline kline() {
        return new DailyKline(LocalDate.of(2026, 8, 28), 9, 10, 8.5, 10.5, 100, 1000, 1.2);
    }
}
