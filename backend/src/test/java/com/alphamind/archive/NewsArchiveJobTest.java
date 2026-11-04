package com.alphamind.archive;

import com.alphamind.news.service.NewsResolution;
import com.alphamind.news.service.StockNewsService;
import com.alphamind.service.StockCatalog.StockMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsArchiveJobTest {
    private final StockNewsService news = mock(StockNewsService.class);
    private final List<StockMetadata> pool = List.of(
            new StockMetadata("600519", "贵州茅台", "白酒", "上海主板"),
            new StockMetadata("000858", "五粮液", "白酒", "深圳主板"),
            new StockMetadata("300750", "宁德时代", "电池", "创业板"));

    @Test
    void visitsEveryStockAndContinuesPastFailures() {
        NewsResolution resolution = mock(NewsResolution.class);
        when(news.news("600519")).thenReturn(Optional.of(resolution));
        when(news.news("000858")).thenThrow(new IllegalStateException("upstream"));
        when(news.news("300750")).thenReturn(Optional.empty());
        List<Long> sleeps = new ArrayList<>();
        var job = new NewsArchiveJob(() -> pool, news, 250, sleeps::add);

        var summary = job.runOnce();

        verify(news).news("600519");
        verify(news).news("000858");
        verify(news).news("300750");
        assertEquals(3, summary.visited());
        assertEquals(1, summary.withNews());
        assertEquals(1, summary.failed());
        assertEquals(List.of(250L, 250L), sleeps, "股票之间限速，最后一只之后不再等待");
    }

    @Test
    void overlappingRunsAreSkipped() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(news.news(anyString())).thenAnswer(inv -> {
            started.countDown();
            release.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return Optional.empty();
        });
        var job = new NewsArchiveJob(() -> pool, news, 0, ms -> {});
        var first = java.util.concurrent.CompletableFuture.supplyAsync(job::runOnce);
        started.await(2, java.util.concurrent.TimeUnit.SECONDS);

        var second = job.runOnce();

        assertEquals(0, second.visited(), "上一次还没跑完时直接跳过");
        release.countDown();
        assertEquals(3, first.get().visited());
    }
}
