package com.alphamind.news.service;

import com.alphamind.news.provider.EastmoneyNewsProvider;
import com.alphamind.news.provider.NewsArticle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockNewsServiceTest {

    private final EastmoneyNewsProvider provider = mock(EastmoneyNewsProvider.class);
    private final NewsCacheService cacheService = mock(NewsCacheService.class);
    private final StockNewsService service = new StockNewsService(provider, cacheService);

    @Test
    void returnsLiveNewsFromProviderAndPopulatesCache() {
        when(cacheService.getFresh("600519")).thenReturn(Optional.empty());
        when(provider.fetchNews("600519", 20)).thenReturn(Optional.of(List.of(
                new NewsArticle("标题一", null, "http://example.com/1"))));
        when(provider.sourceName()).thenReturn("eastmoney");

        Optional<NewsResolution> result = service.news("600519");

        assertTrue(result.isPresent());
        NewsResolution resolution = result.get();
        assertEquals(NewsResolution.Status.LIVE, resolution.status());
        assertFalse(resolution.stale());
        assertEquals(1, resolution.snapshot().articles().size());
        assertEquals("eastmoney", resolution.snapshot().sourceName());
        verify(cacheService).put(resolution.snapshot());
    }

    @Test
    void returnsFreshCacheWithoutCallingProvider() {
        NewsSnapshot snapshot = new NewsSnapshot(
                "600519", List.of(new NewsArticle("缓存标题", null, null)), "eastmoney", Instant.now());
        when(cacheService.getFresh("600519"))
                .thenReturn(Optional.of(new NewsCacheService.CacheHit(snapshot, 30)));

        Optional<NewsResolution> result = service.news("600519");

        assertTrue(result.isPresent());
        assertEquals(NewsResolution.Status.FRESH_CACHE, result.get().status());
        assertEquals(30, result.get().cacheAgeSeconds());
        verify(provider, never()).fetchNews(anyString(), anyInt());
    }

    @Test
    void fallsBackToStaleCacheWithWarningWhenProviderFails() {
        when(cacheService.getFresh("600519")).thenReturn(Optional.empty());
        when(provider.fetchNews("600519", 20)).thenReturn(Optional.empty());
        NewsSnapshot snapshot = new NewsSnapshot(
                "600519", List.of(new NewsArticle("旧标题", null, null)), "eastmoney",
                Instant.now().minusSeconds(7200));
        when(cacheService.getStale("600519"))
                .thenReturn(Optional.of(new NewsCacheService.CacheHit(snapshot, 7200)));

        Optional<NewsResolution> result = service.news("600519");

        assertTrue(result.isPresent());
        NewsResolution resolution = result.get();
        assertEquals(NewsResolution.Status.STALE_CACHE, resolution.status());
        assertTrue(resolution.stale());
        assertNotNull(resolution.warning());
    }

    @Test
    void returnsEmptyWhenNewsSourceAndCacheAreUnavailable() {
        when(cacheService.getFresh("600519")).thenReturn(Optional.empty());
        when(provider.fetchNews("600519", 20)).thenReturn(Optional.empty());
        when(cacheService.getStale("600519")).thenReturn(Optional.empty());

        assertTrue(service.news("600519").isEmpty());
    }
}
