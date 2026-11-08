package com.alphamind.portfolio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioSnapshotJobTest {
    @Test
    void overlappingRunsAreSkippedAndFailuresContained() throws Exception {
        var service = mock(PortfolioSnapshotService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(service.record()).thenAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return new PortfolioSnapshotService.Result(PortfolioSnapshotService.Outcome.RECORDED,
                    LocalDate.parse("2026-11-09"), 50_000.0, List.of());
        }).thenThrow(new IllegalStateException("db down"));
        var job = new PortfolioSnapshotJob(service);
        var first = CompletableFuture.supplyAsync(job::runOnce);
        started.await(2, TimeUnit.SECONDS);

        assertTrue(job.runOnce().isEmpty(), "上一轮还没结束时跳过");
        release.countDown();
        assertEquals(PortfolioSnapshotService.Outcome.RECORDED, first.get().orElseThrow().outcome());
        assertTrue(job.runOnce().isEmpty(), "异常被吞掉并返回 empty");
    }
}
