package com.alphamind.investment;

import com.alphamind.model.dto.MarketDataDTO;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Wilder RMA：RSI(14)、ATR(20)；周线仅使用已结束周，不以 RSI6/12 代替 RSI14。 */
public final class PersonalTimingCalculator {
    private PersonalTimingCalculator() {}
    public static PersonalModelAssessment.Timing calculate(MarketDataDTO market, LocalDate today) {
        if (market == null || market.getKlines() == null || market.getKlines().isEmpty()) return empty();
        List<double[]> bars = market.getKlines();
        if (bars.stream().anyMatch(b -> b == null || b.length < 4 || Arrays.stream(b).anyMatch(v -> !Double.isFinite(v) || v <= 0)
                || b[3] < b[2])) return empty();
        List<Double> closes = bars.stream().map(b -> b[1]).toList();
        List<Double> ranges = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            double[] b = bars.get(i);
            double tr = b[3] - b[2];
            if (i > 0) tr = Math.max(tr, Math.max(Math.abs(b[3] - closes.get(i - 1)), Math.abs(b[2] - closes.get(i - 1))));
            ranges.add(tr);
        }
        Double weekly = null;
        String asOf = null;
        if (market.getKlineDates() != null && market.getKlineDates().size() == bars.size()) {
            try {
                Map<LocalDate, Double> weeks = new TreeMap<>();
                LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                for (int i = 0; i < closes.size(); i++) {
                    LocalDate date = LocalDate.parse(market.getKlineDates().get(i));
                    LocalDate week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    if (week.isBefore(currentWeek)) weeks.put(week, closes.get(i));
                }
                weekly = rsi(new ArrayList<>(weeks.values()), 14);
                asOf = market.getKlineDates().get(bars.size() - 1);
            } catch (RuntimeException ignored) { /* 日期不足或不可用时不编造周 RSI。 */ }
        }
        Double atr = rma(ranges, 20);
        Double pct = atr != null && market.getCurrentPrice() != null && market.getCurrentPrice() > 0
                ? round(atr / market.getCurrentPrice() * 100) : null;
        return new PersonalModelAssessment.Timing(rsi(closes, 14), weekly, atr == null ? null : round(atr), pct,
                round(ranges.get(ranges.size() - 1)), null, asOf);
    }
    static Double rsi(List<Double> closes, int period) {
        if (closes.size() <= period) return null;
        List<Double> gains = new ArrayList<>(), losses = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gains.add(Math.max(0, change)); losses.add(Math.max(0, -change));
        }
        double gain = rma(gains, period), loss = rma(losses, period);
        return gain == 0 && loss == 0 ? 50D : loss == 0 ? 100D : round(100 - 100 / (1 + gain / loss));
    }
    private static Double rma(List<Double> values, int period) {
        if (values.size() < period) return null;
        double average = values.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        for (int i = period; i < values.size(); i++) average = (average * (period - 1) + values.get(i)) / period;
        return average;
    }
    private static PersonalModelAssessment.Timing empty() { return new PersonalModelAssessment.Timing(null,null,null,null,null,null,null); }
    static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
