package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CachedKlineSourceTest {
    @TempDir Path dir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<DailyKline> bars = List.of(
            new DailyKline(LocalDate.of(2026, 10, 22), 10, 10.5, 9.8, 10.6, 1000, 10500, 1.2),
            new DailyKline(LocalDate.of(2026, 10, 23), 10.5, 10.2, 10.1, 10.7, 900, 9300, 1.0));
    private final AtomicInteger calls = new AtomicInteger();

    private Function<String, Optional<List<DailyKline>>> fetcher(Optional<List<DailyKline>> result) {
        return code -> { calls.incrementAndGet(); return result; };
    }

    @Test
    void fetchesAndWritesWhenMissing() {
        var source = new CachedKlineSource(dir, fetcher(Optional.of(bars)), false, mapper);
        assertEquals(bars, source.load("600036").orElseThrow());
        assertEquals(1, calls.get());
        assertTrue(Files.exists(dir.resolve("600036.json")));
    }

    @Test
    void readsCacheWithoutFetching() {
        new CachedKlineSource(dir, fetcher(Optional.of(bars)), false, mapper).load("600036");
        calls.set(0);
        var cachedOnly = new CachedKlineSource(dir, fetcher(Optional.empty()), false, mapper);
        assertEquals(bars, cachedOnly.load("600036").orElseThrow());
        assertEquals(0, calls.get());
    }

    @Test
    void refreshIgnoresCache() {
        new CachedKlineSource(dir, fetcher(Optional.of(bars)), false, mapper).load("600036");
        calls.set(0);
        var refreshed = new CachedKlineSource(dir, fetcher(Optional.of(bars.subList(0, 1))), true, mapper);
        assertEquals(1, refreshed.load("600036").orElseThrow().size());
        assertEquals(1, calls.get());
    }

    @Test
    void fetchFailureReturnsEmptyAndWritesNothing() {
        var source = new CachedKlineSource(dir, fetcher(Optional.empty()), false, mapper);
        assertTrue(source.load("517400").isEmpty());
        assertFalse(Files.exists(dir.resolve("517400.json")));
    }

    @Test
    void refreshFailureFallsBackToCacheWithNote() {
        new CachedKlineSource(dir, fetcher(Optional.of(bars)), false, mapper).load("600036");
        var refreshed = new CachedKlineSource(dir, fetcher(Optional.empty()), true, mapper);
        assertEquals(bars, refreshed.load("600036").orElseThrow());
        assertEquals(1, refreshed.notes().size());
        assertTrue(refreshed.notes().get(0).contains("600036") && refreshed.notes().get(0).contains("沿用"));
        assertTrue(refreshed.load("000001").isEmpty(), "无缓存时仍返回 empty");
        assertEquals(1, refreshed.notes().size());
    }
}
