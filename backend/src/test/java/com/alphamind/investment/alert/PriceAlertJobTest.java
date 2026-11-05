package com.alphamind.investment.alert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceAlertJobTest {
    @Test
    void overlappingRunsAreSkippedAndFailuresContained() throws Exception {
        var service = mock(PriceAlertService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(service.checkAll()).thenAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return new PriceAlertService.Summary(13, 1, List.of(), List.of());
        }).thenThrow(new IllegalStateException("db down"));
        var job = new PriceAlertJob(service);
        var first = CompletableFuture.supplyAsync(job::runOnce);
        started.await(2, TimeUnit.SECONDS);

        assertTrue(job.runOnce().isEmpty(), "上一轮还没结束时跳过");
        release.countDown();
        assertEquals(1, first.get().orElseThrow().created());
        assertTrue(job.runOnce().isEmpty(), "异常被吞掉并返回 empty");
    }
}
