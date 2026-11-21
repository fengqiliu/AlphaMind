package com.alphamind.portfolio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioHealthJobTest {
    @Test
    void overlappingRunsAreSkippedAndFailuresContained() throws Exception {
        var service = mock(PortfolioHealthAlertService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(service.check()).thenAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return new PortfolioHealthAlertService.Summary(PortfolioHealthAlertService.Outcome.CHECKED, 2, 1, 0, List.of());
        }).thenThrow(new IllegalStateException("db down"));
        var job = new PortfolioHealthJob(service);
        var first = CompletableFuture.supplyAsync(job::runOnce);
        started.await(2, TimeUnit.SECONDS);

        assertTrue(job.runOnce().isEmpty(), "上一轮还没结束时跳过");
        release.countDown();
        assertEquals(1, first.get().orElseThrow().created());
        assertTrue(job.runOnce().isEmpty(), "异常被吞掉并返回 empty");
    }
}
