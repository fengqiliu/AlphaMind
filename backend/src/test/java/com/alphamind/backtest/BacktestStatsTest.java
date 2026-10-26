package com.alphamind.backtest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktestStatsTest {
    private static final LocalDate D0 = LocalDate.of(2025, 1, 1);

    private static BacktestSample s(String code, int day, int score, int h, double net) {
        return new BacktestSample(code, D0.plusDays(day), score, h, net, net);
    }

    @Test
    void excessSubtractsSameDateMean() {
        var rows = BacktestStats.withExcess(List.of(
                s("A", 0, 80, 10, 0.03), s("B", 0, 30, 10, -0.01),
                s("A", 1, 80, 10, 0.10), s("A", 0, 80, 5, 0.50)));
        assertEquals(0.02, rows.get(0).excess(), 1e-12);
        assertEquals(-0.02, rows.get(1).excess(), 1e-12);
        assertEquals(0.0, rows.get(2).excess(), 1e-12, "同日只有一只时超额为 0");
        assertEquals(0.0, rows.get(3).excess(), 1e-12, "不同持有期不混合");
    }

    @Test
    void bucketsSplitAtSeventyAndForty() {
        var rows = BacktestStats.withExcess(List.of(
                s("A", 0, 70, 10, 0.04), s("B", 0, 69, 10, 0.00), s("C", 0, 40, 10, 0.02),
                s("D", 0, 39, 10, -0.06), s("E", 0, 95, 5, 0.9)));
        var buckets = BacktestStats.buckets(rows, 10, BacktestStats.STANDARD_RANGES);
        assertEquals(List.of("≥70", "40–69", "<40"), buckets.stream().map(BucketStats::label).toList());
        assertEquals(List.of(1, 2, 1), buckets.stream().map(BucketStats::count).toList());
        assertEquals(0.01, buckets.get(1).meanNet(), 1e-12);
        assertEquals(0.01, buckets.get(1).medianNet(), 1e-12);
        assertEquals(0.5, buckets.get(1).winRate(), 1e-12, "净收益 0 不算盈利");
        assertEquals(0.04, buckets.get(0).meanExcess(), 1e-12);
    }

    @Test
    void emptyBucketYieldsNullStats() {
        var buckets = BacktestStats.buckets(BacktestStats.withExcess(List.of(s("A", 0, 50, 10, 0.01))), 10,
                BacktestStats.STANDARD_RANGES);
        var high = buckets.get(0);
        assertEquals(0, high.count());
        assertNull(high.meanNet());
        assertNull(high.medianNet());
        assertNull(high.winRate());
        assertNull(high.meanExcess());
    }

    @Test
    void spearmanUsesAverageRanksForTies() {
        assertEquals(0.8660254, BacktestStats.spearman(new double[]{1, 1, 2}, new double[]{1, 2, 3}), 1e-6);
        assertEquals(-1.0, BacktestStats.spearman(new double[]{1, 2, 3}, new double[]{9, 5, 1}), 1e-12);
        assertTrue(Double.isNaN(BacktestStats.spearman(new double[]{5, 5, 5}, new double[]{1, 2, 3})), "全部同分时无定义");
    }

    @Test
    void icSkipsDatesWithFewerThanFiveStocks() {
        List<BacktestSample> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(s("S" + i, 0, 40 + i * 10, 10, i * 0.01));
        for (int i = 0; i < 4; i++) rows.add(s("S" + i, 1, 40 + i * 10, 10, -i * 0.01));
        var ic = BacktestStats.ic(rows, 10);
        assertEquals(1, ic.dates());
        assertEquals(1.0, ic.meanIc(), 1e-12);
        assertNull(ic.tNonOverlap(), "只有一个 IC 时无法计算 t 值");
    }

    @Test
    void tStatUsesEveryHthDate() {
        // h=2：IC 序列 +1, -1, +1, -1, +1, -1 → 不重叠取第 0/2/4 个 = 全为 +1；再加一天 0.5 打破零方差
        List<BacktestSample> rows = new ArrayList<>();
        double[] sign = {1, -1, 1, -1, 1, -1};
        for (int d = 0; d < sign.length; d++)
            for (int i = 0; i < 5; i++) rows.add(s("S" + i, d, 40 + i * 10, 2, sign[d] * i * 0.01));
        int[] partial = {1, 2, 3, 5, 4}; // spearman = 0.9
        for (int i = 0; i < 5; i++) rows.add(s("S" + i, 6, 40 + i * 10, 2, partial[i] * 0.01));
        var ic = BacktestStats.ic(rows, 2);
        assertEquals(7, ic.dates());
        // 不重叠序列：day0=1, day2=1, day4=1, day6=0.9 → mean 0.975, sd 0.05, t = 0.975/(0.05/2) = 39
        assertEquals(39.0, ic.tNonOverlap(), 1e-9);
        assertEquals(4.0 / 7, ic.positiveShare(), 1e-12);
    }

    @Test
    void splitDateIsSeventyPercentOfDistinctDates() {
        List<BacktestSample> rows = new ArrayList<>();
        for (int d = 0; d < 10; d++) { rows.add(s("A", d, 50, 5, 0)); rows.add(s("B", d, 50, 5, 0)); }
        assertEquals(D0.plusDays(7), BacktestStats.splitDate(rows, 0.7));
    }

    @Test
    void verdictRequiresBothConditions() {
        var high = new BucketStats("≥70", 10, 0.02, 0.02, 0.6, 0.010);
        var low = new BucketStats("<40", 10, 0.00, 0.00, 0.4, -0.005);
        var strong = new IcStats(100, 0.05, 0.6, 2.5);
        var weakIc = new IcStats(100, 0.05, 0.6, 1.2);
        assertEquals(Verdict.DISCRIMINATIVE, BacktestStats.verdict(high, low, strong));
        assertEquals(Verdict.WEAK, BacktestStats.verdict(high, low, weakIc));
        assertEquals(Verdict.WEAK, BacktestStats.verdict(low, high, strong));
        assertEquals(Verdict.NONE, BacktestStats.verdict(low, high, weakIc));
        var empty = new BucketStats("≥70", 0, null, null, null, null);
        assertEquals(Verdict.NONE, BacktestStats.verdict(empty, low, new IcStats(0, null, null, null)));
    }
}
