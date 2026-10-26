package com.alphamind.backtest;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 回测统计：超额收益、分数段、横截面 Spearman IC、样本切分与判定。 */
public final class BacktestStats {

    /** 分数段 [low, highExclusive)。 */
    public record ScoreRange(String label, int low, int highExclusive) {}

    public record ExcessSample(BacktestSample sample, double excess) {}

    public static final List<ScoreRange> STANDARD_RANGES = List.of(
            new ScoreRange("≥70", 70, 101), new ScoreRange("40–69", 40, 70), new ScoreRange("<40", 0, 40));

    /** 10 分一档的细分（技术分限幅 10–95）。 */
    public static final List<ScoreRange> FINE_RANGES = List.of(
            new ScoreRange("90–95", 90, 101), new ScoreRange("80–89", 80, 90), new ScoreRange("70–79", 70, 80),
            new ScoreRange("60–69", 60, 70), new ScoreRange("50–59", 50, 60), new ScoreRange("40–49", 40, 50),
            new ScoreRange("30–39", 30, 40), new ScoreRange("20–29", 20, 30), new ScoreRange("10–19", 0, 20));

    static final int MIN_STOCKS_PER_DATE = 5;

    private BacktestStats() {}

    /** 超额 = 净收益 − 同一信号日、同一持有期全池净收益均值。 */
    public static List<ExcessSample> withExcess(List<BacktestSample> samples) {
        Map<String, Double> means = samples.stream().collect(Collectors.groupingBy(
                s -> s.signalDate() + "|" + s.horizon(), Collectors.averagingDouble(BacktestSample::netReturn)));
        return samples.stream()
                .map(s -> new ExcessSample(s, s.netReturn() - means.get(s.signalDate() + "|" + s.horizon())))
                .toList();
    }

    public static List<BucketStats> buckets(List<ExcessSample> rows, int horizon, List<ScoreRange> ranges) {
        List<BucketStats> result = new ArrayList<>();
        for (ScoreRange range : ranges) {
            List<ExcessSample> in = rows.stream()
                    .filter(r -> r.sample().horizon() == horizon
                            && r.sample().score() >= range.low() && r.sample().score() < range.highExclusive())
                    .toList();
            if (in.isEmpty()) {
                result.add(new BucketStats(range.label(), 0, null, null, null, null));
                continue;
            }
            double[] net = in.stream().mapToDouble(r -> r.sample().netReturn()).sorted().toArray();
            double median = net.length % 2 == 1 ? net[net.length / 2] : (net[net.length / 2 - 1] + net[net.length / 2]) / 2;
            result.add(new BucketStats(range.label(), in.size(),
                    Arrays.stream(net).average().orElseThrow(), median,
                    (double) Arrays.stream(net).filter(v -> v > 0).count() / net.length,
                    in.stream().mapToDouble(ExcessSample::excess).average().orElseThrow()));
        }
        return result;
    }

    /** Spearman 秩相关，并列取平均秩；任一侧无方差时返回 NaN。 */
    public static double spearman(double[] x, double[] y) {
        return pearson(ranks(x), ranks(y));
    }

    public static IcStats ic(List<BacktestSample> samples, int horizon) {
        TreeMap<LocalDate, List<BacktestSample>> byDate = samples.stream()
                .filter(s -> s.horizon() == horizon)
                .collect(Collectors.groupingBy(BacktestSample::signalDate, TreeMap::new, Collectors.toList()));
        List<Double> ics = new ArrayList<>();
        for (List<BacktestSample> day : byDate.values()) {
            if (day.size() < MIN_STOCKS_PER_DATE) continue;
            double value = spearman(day.stream().mapToDouble(BacktestSample::score).toArray(),
                    day.stream().mapToDouble(BacktestSample::netReturn).toArray());
            if (!Double.isNaN(value)) ics.add(value);
        }
        if (ics.isEmpty()) return new IcStats(0, null, null, null);
        double mean = ics.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double positive = (double) ics.stream().filter(v -> v > 0).count() / ics.size();
        return new IcStats(ics.size(), mean, positive, tStat(ics, horizon));
    }

    /** 按去重排序后的信号日取第 {@code share} 分位作为切分点；早于它的为样本内。 */
    public static LocalDate splitDate(List<BacktestSample> samples, double share) {
        List<LocalDate> dates = samples.stream().map(BacktestSample::signalDate).distinct().sorted().toList();
        if (dates.isEmpty()) return null;
        return dates.get(Math.min(dates.size() - 1, (int) Math.floor(dates.size() * share)));
    }

    public static Verdict verdict(BucketStats high, BucketStats low, IcStats ic) {
        boolean spread = high.meanExcess() != null && low.meanExcess() != null && high.meanExcess() - low.meanExcess() > 0;
        boolean ranking = ic.meanIc() != null && ic.meanIc() > 0 && ic.tOverlapAdjusted() != null && ic.tOverlapAdjusted() > 2;
        if (spread && ranking) return Verdict.DISCRIMINATIVE;
        if (spread || ranking) return Verdict.WEAK;
        return Verdict.NONE;
    }

    /**
     * 持有期重叠修正的 t 值：均值取全部 IC，标准误按有效独立样本数 n/h 放大
     * （相邻 h 日的持有期重叠，IC 并不独立）。与抽样起点无关，与展示的 IC 均值同源。
     */
    private static Double tStat(List<Double> values, int horizon) {
        double effective = (double) values.size() / horizon;
        if (effective < 2) return null;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (values.size() - 1);
        if (variance == 0) return null;
        return mean / (Math.sqrt(variance) / Math.sqrt(effective));
    }

    private static double[] ranks(double[] values) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[values.length];
        for (int start = 0; start < order.length; ) {
            int end = start;
            while (end + 1 < order.length && values[order[end + 1]] == values[order[start]]) end++;
            double average = (start + end) / 2.0 + 1;
            for (int i = start; i <= end; i++) ranks[order[i]] = average;
            start = end + 1;
        }
        return ranks;
    }

    private static double pearson(double[] x, double[] y) {
        double mx = Arrays.stream(x).average().orElse(Double.NaN);
        double my = Arrays.stream(y).average().orElse(Double.NaN);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxx == 0 || syy == 0 ? Double.NaN : sxy / Math.sqrt(sxx * syy);
    }
}
