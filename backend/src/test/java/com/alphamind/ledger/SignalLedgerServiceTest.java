package com.alphamind.ledger;

import com.alphamind.market.provider.DailyKline;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SignalLedgerServiceTest {
    private final SignalOutcomeRepository outcomes = mock(SignalOutcomeRepository.class);
    private final Map<String, Integer> fetchCalls = new HashMap<>();
    private final AtomicInteger benchmarkCalls = new AtomicInteger();

    record Candidate(String getId, String getStockCode, Long getCreatedAtMillis, String getSignalType,
                     Double getConfidenceValue, String getStrategy, String getAnalysisMode, String getPersonalLevel)
            implements LedgerCandidate {}

    private static List<DailyKline> bars(int n) {
        List<DailyKline> out = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 9, 1);
        for (int i = 0; i < n; i++) {
            while (day.getDayOfWeek().getValue() >= 6) day = day.plusDays(1);
            out.add(new DailyKline(day, 10 + i * 0.1, 10.05 + i * 0.1, 9.9 + i * 0.1, 10.2 + i * 0.1, 100, 1000, 1));
            day = day.plusDays(1);
        }
        return out;
    }

    private SignalLedgerService service(List<DailyKline> stockBars) {
        return new SignalLedgerService(outcomes,
                (code, limit) -> { fetchCalls.merge(code, 1, Integer::sum); return Optional.of(stockBars); },
                limit -> { benchmarkCalls.incrementAndGet(); return Optional.of(stockBars); },
                Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC));
    }

    private static Candidate candidate(String id, String code, String createdAt) {
        return new Candidate(id, code, Instant.parse(createdAt).toEpochMilli(), "BUY", 0.72, "BALANCED", "DEBATE", null);
    }

    @Test
    void writesAllMaturedHorizonsWithFrozenReportAttributes() {
        when(outcomes.findPendingCandidates()).thenReturn(List.of(candidate("r1", "600519", "2026-09-10T03:00:00Z")));
        when(outcomes.findByReportIdIn(any())).thenReturn(List.of());

        var summary = service(bars(60)).evaluatePending();

        assertEquals(3, summary.written());
        verify(outcomes, times(3)).save(argThat(row -> "r1".equals(row.getReportId())
                && "BUY".equals(row.getSignalType()) && row.getConfidenceValue() == 0.72
                && "DEBATE".equals(row.getAnalysisMode()) && row.getSignalDate().equals(LocalDate.of(2026, 9, 10))));
    }

    @Test
    void signalDateUsesShanghaiCalendarDay() {
        // 2026-09-10T17:00Z 已是上海 9 月 11 日凌晨
        when(outcomes.findPendingCandidates()).thenReturn(List.of(candidate("r1", "600519", "2026-09-10T17:00:00Z")));
        when(outcomes.findByReportIdIn(any())).thenReturn(List.of());

        service(bars(60)).evaluatePending();

        verify(outcomes, atLeastOnce()).save(argThat(row -> row.getSignalDate().equals(LocalDate.of(2026, 9, 11))));
    }

    @Test
    void skipsHorizonsAlreadyWrittenAndOnesNotYetMatured() {
        when(outcomes.findPendingCandidates()).thenReturn(List.of(candidate("r1", "600519", "2026-09-10T03:00:00Z")));
        var existing = SignalOutcomeEntity.builder().reportId("r1").horizonDays(5).build();
        when(outcomes.findByReportIdIn(any())).thenReturn(List.of(existing));

        // 9-10 是第 7 根（下标从 0 起）；共 20 根时 10 日到期（需下标 17）、20 日未到期（需下标 27）
        var summary = service(bars(20)).evaluatePending();

        assertEquals(1, summary.written());
        verify(outcomes).save(argThat(row -> row.getHorizonDays() == 10));
        assertEquals(1, summary.pending());
    }

    @Test
    void fetchesEachStockAndBenchmarkOnceAndToleratesFetchFailures() {
        when(outcomes.findPendingCandidates()).thenReturn(List.of(
                candidate("r1", "600519", "2026-09-10T03:00:00Z"),
                candidate("r2", "600519", "2026-09-11T03:00:00Z"),
                candidate("r3", "000001", "2026-09-10T03:00:00Z")));
        when(outcomes.findByReportIdIn(any())).thenReturn(List.of());
        var svc = new SignalLedgerService(outcomes,
                (code, limit) -> {
                    fetchCalls.merge(code, 1, Integer::sum);
                    if (code.equals("000001")) throw new IllegalStateException("upstream");
                    return Optional.of(bars(60));
                },
                limit -> { benchmarkCalls.incrementAndGet(); return Optional.of(bars(60)); },
                Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC));

        var summary = svc.evaluatePending();

        assertEquals(Map.of("600519", 1, "000001", 1), fetchCalls);
        assertEquals(1, benchmarkCalls.get());
        assertEquals(6, summary.written());
        assertEquals(List.of("000001"), summary.failedStocks());
    }

    @Test
    void nothingPendingDoesNoNetworkWork() {
        when(outcomes.findPendingCandidates()).thenReturn(List.of());

        var summary = service(bars(60)).evaluatePending();

        assertEquals(0, summary.written());
        assertEquals(0, benchmarkCalls.get());
        assertTrue(fetchCalls.isEmpty());
    }
}
