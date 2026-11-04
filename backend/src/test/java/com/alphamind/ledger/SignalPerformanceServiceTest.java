package com.alphamind.ledger;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignalPerformanceServiceTest {
    private final SignalOutcomeRepository repo = mock(SignalOutcomeRepository.class);

    private static SignalOutcomeEntity row(String signal, int horizon, double excess, Double confidence, boolean blocked) {
        return SignalOutcomeEntity.builder().reportId(signal + excess).stockCode("600519").horizonDays(horizon)
                .signalType(signal).confidenceValue(confidence).strategy("BALANCED").analysisMode("DEBATE")
                .signalDate(LocalDate.of(2026, 9, 1)).entryDate(LocalDate.of(2026, 9, 2)).exitDate(LocalDate.of(2026, 9, 8))
                .netReturn(excess + 0.01).excessReturn(excess).entryBlocked(blocked).build();
    }

    private static PerformanceRow find(List<PerformanceRow> rows, String dimension, String key, int horizon) {
        return rows.stream().filter(r -> r.dimension().equals(dimension) && r.key().equals(key) && r.horizonDays() == horizon)
                .findFirst().orElseThrow();
    }

    @Test
    void hitRateCountsBuyAboveBenchmarkAndSellBelowAndIgnoresHold() {
        when(repo.findAll()).thenReturn(List.of(
                row("BUY", 10, 0.02, 0.8, false), row("BUY", 10, -0.01, 0.8, false),
                row("SELL", 10, -0.03, 0.5, false), row("HOLD", 10, 0.05, 0.5, false)));

        var rows = new SignalPerformanceService(repo).summary().rows();

        var buy = find(rows, "signal", "BUY", 10);
        assertEquals(2, buy.count());
        assertEquals(0.5, buy.hitRate(), 1e-12);
        assertEquals(0.005, buy.meanExcessReturn(), 1e-12);
        assertEquals(1.0, find(rows, "signal", "SELL", 10).hitRate(), 1e-12, "SELL 跑输基准算对");
        assertNull(find(rows, "signal", "HOLD", 10).hitRate(), "HOLD 不算胜率");
        var all = find(rows, "mode", "DEBATE", 10);
        assertEquals(4, all.count());
        assertEquals(2.0 / 3, all.hitRate(), 1e-12, "混合分组只用有方向的信号计算胜率");
    }

    @Test
    void blockedEntriesAndMissingExcessAreExcludedFromStats() {
        var noBench = SignalOutcomeEntity.builder().reportId("x").stockCode("600519").horizonDays(5).signalType("BUY")
                .signalDate(LocalDate.of(2026, 9, 1)).entryDate(LocalDate.of(2026, 9, 2)).exitDate(LocalDate.of(2026, 9, 8))
                .netReturn(0.03).excessReturn(null).entryBlocked(false).build();
        when(repo.findAll()).thenReturn(List.of(row("BUY", 5, 0.02, 0.8, true), noBench));

        var summary = new SignalPerformanceService(repo).summary();
        var buy = find(summary.rows(), "signal", "BUY", 5);

        assertEquals(1, buy.count(), "涨停无法买入的不计入");
        assertEquals(0.03, buy.meanNetReturn(), 1e-12);
        assertNull(buy.meanExcessReturn(), "没有基准数据时超额与胜率为空，不当作 0");
        assertNull(buy.hitRate());
        assertEquals(1, summary.blockedCount());
    }

    @Test
    void confidenceBucketsOnlyCoverBuySignalsAndSmallSamplesAreFlagged() {
        List<SignalOutcomeEntity> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) rows.add(row("BUY", 20, 0.01, 0.75, false));
        rows.add(row("BUY", 20, -0.01, 0.30, false));
        rows.add(row("SELL", 20, -0.01, 0.75, false));
        when(repo.findAll()).thenReturn(rows);

        var out = new SignalPerformanceService(repo).summary().rows();

        var high = find(out, "buyConfidence", "≥70", 20);
        assertEquals(25, high.count(), "只统计 BUY");
        assertFalse(high.insufficient());
        assertTrue(find(out, "buyConfidence", "<40", 20).insufficient(), "少于 20 条标注样本不足");
    }

    @Test
    void missingAttributesGroupAsNotRecorded() {
        var bare = SignalOutcomeEntity.builder().reportId("y").stockCode("600519").horizonDays(5)
                .signalDate(LocalDate.of(2026, 9, 1)).entryDate(LocalDate.of(2026, 9, 2)).exitDate(LocalDate.of(2026, 9, 8))
                .netReturn(0.01).excessReturn(0.0).entryBlocked(false).personalLevel("S2").build();
        when(repo.findAll()).thenReturn(List.of(bare));

        var rows = new SignalPerformanceService(repo).summary().rows();

        assertEquals(1, find(rows, "signal", "未记录", 5).count());
        assertEquals(1, find(rows, "personalLevel", "S2", 5).count());
    }
}
