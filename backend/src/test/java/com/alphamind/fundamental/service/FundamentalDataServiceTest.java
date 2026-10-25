package com.alphamind.fundamental.service;

import com.alphamind.fundamental.provider.EastmoneyFundamentalProvider;
import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FundamentalDataServiceTest {
    private final EastmoneyFundamentalProvider provider = mock(EastmoneyFundamentalProvider.class);
    private final FundamentalCacheService cache = mock(FundamentalCacheService.class);
    private final FundamentalDataService service = new FundamentalDataService(provider, cache);

    private final ValuationMetrics valuation = new ValuationMetrics(LocalDate.of(2026, 10, 23), 41.77, 1.05e12,
            6.94, 0.92, 1.69, 3.05, 5.71, 12.3, 8.4, 1250, 1250, LocalDate.of(2021, 10, 25));

    @Test
    void freshCacheIsServedWithoutHittingProvider() {
        var snapshot = new FundamentalSnapshot("600036", valuation, null, "eastmoney-datacenter", Instant.now());
        when(cache.getFresh("600036")).thenReturn(Optional.of(new FundamentalCacheService.CacheHit(snapshot, 60)));

        var resolution = service.fundamentals("600036").orElseThrow();

        assertEquals(FundamentalResolution.Status.FRESH_CACHE, resolution.status());
        verifyNoInteractions(provider);
    }

    @Test
    void partialLiveDataIsCachedAndReturned() {
        when(cache.getFresh("600036")).thenReturn(Optional.empty());
        when(provider.fetchValuation("600036")).thenReturn(Optional.of(valuation));
        when(provider.fetchFinancials("600036")).thenReturn(Optional.empty());
        when(provider.sourceName()).thenReturn("eastmoney-datacenter");

        var resolution = service.fundamentals("600036").orElseThrow();

        assertEquals(FundamentalResolution.Status.LIVE, resolution.status());
        assertSame(valuation, resolution.snapshot().valuation());
        assertNull(resolution.snapshot().financials());
        verify(cache).put(any());
    }

    @Test
    void providerFailureFallsBackToLabelledStaleCache() {
        var financials = new FinancialMetrics("2026中报", LocalDate.of(2026, 6, 30), null, "银行",
                2.98, 2.98, 12.08, 1.78e11, 7.6e10, 7.6e10, 4.8, 2.0, 2.0, 6.71, null, 90.2);
        var snapshot = new FundamentalSnapshot("600036", null, financials, "eastmoney-datacenter", Instant.now());
        when(cache.getFresh("600036")).thenReturn(Optional.empty());
        when(provider.fetchValuation("600036")).thenReturn(Optional.empty());
        when(provider.fetchFinancials("600036")).thenReturn(Optional.empty());
        when(cache.getStale("600036")).thenReturn(Optional.of(new FundamentalCacheService.CacheHit(snapshot, 90000)));

        var resolution = service.fundamentals("600036").orElseThrow();

        assertTrue(resolution.stale());
        assertTrue(resolution.warning().contains("90000 秒前"));
        verify(cache, never()).put(any());
    }

    @Test
    void returnsEmptyInsteadOfInventingDataWhenNothingAvailable() {
        when(cache.getFresh("517400")).thenReturn(Optional.empty());
        when(provider.fetchValuation("517400")).thenReturn(Optional.empty());
        when(provider.fetchFinancials("517400")).thenReturn(Optional.empty());
        when(cache.getStale("517400")).thenReturn(Optional.empty());

        assertTrue(service.fundamentals("517400").isEmpty());
    }
}
