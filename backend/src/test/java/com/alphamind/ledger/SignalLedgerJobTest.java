package com.alphamind.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignalLedgerJobTest {
    @Test
    void overlappingRunsAreSkipped() throws Exception {
        var ledger = mock(SignalLedgerService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(ledger.evaluatePending()).thenAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return new SignalLedgerService.Summary(3, 0, List.of());
        });
        var job = new SignalLedgerJob(ledger);
        var first = CompletableFuture.supplyAsync(job::runOnce);
        started.await(2, TimeUnit.SECONDS);

        assertTrue(job.runOnce().isEmpty(), "上一轮还没结束时跳过");
        release.countDown();
        assertEquals(3, first.get().orElseThrow().written());
    }

    @Test
    void failuresAreContainedAndReported() {
        var ledger = mock(SignalLedgerService.class);
        when(ledger.evaluatePending()).thenThrow(new IllegalStateException("db down"));

        assertTrue(new SignalLedgerJob(ledger).runOnce().isEmpty());
    }
}
