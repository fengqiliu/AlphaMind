package com.alphamind.ledger;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 从信号账本汇总事后表现。只读，不调整任何评分规则。 */
@Service
public class SignalPerformanceService {

    /** 少于该样本数时标注“样本不足，仅供参考”。 */
    public static final int MIN_SAMPLES = 20;
    static final String NOT_RECORDED = "未记录";

    public record Summary(int evaluatedCount, int blockedCount, List<PerformanceRow> rows) {}

    private final SignalOutcomeRepository repository;

    public SignalPerformanceService(SignalOutcomeRepository repository) {
        this.repository = repository;
    }

    public Summary summary() {
        List<SignalOutcomeEntity> all = repository.findAll();
        List<SignalOutcomeEntity> usable = all.stream().filter(o -> !o.isEntryBlocked()).toList();
        List<PerformanceRow> rows = new ArrayList<>();
        rows.addAll(group("signal", usable, o -> orNotRecorded(o.getSignalType())));
        rows.addAll(group("buyConfidence",
                usable.stream().filter(o -> "BUY".equals(o.getSignalType()) && o.getConfidenceValue() != null).toList(),
                o -> confidenceBucket(o.getConfidenceValue())));
        rows.addAll(group("strategy", usable, o -> orNotRecorded(o.getStrategy())));
        rows.addAll(group("mode", usable, o -> orNotRecorded(o.getAnalysisMode())));
        rows.addAll(group("personalLevel",
                usable.stream().filter(o -> o.getPersonalLevel() != null).toList(), SignalOutcomeEntity::getPersonalLevel));
        return new Summary(usable.size(), all.size() - usable.size(), rows);
    }

    private static List<PerformanceRow> group(String dimension, List<SignalOutcomeEntity> rows,
                                              Function<SignalOutcomeEntity, String> key) {
        Map<String, Map<Integer, List<SignalOutcomeEntity>>> grouped = rows.stream().collect(Collectors.groupingBy(
                key, TreeMap::new, Collectors.groupingBy(SignalOutcomeEntity::getHorizonDays, TreeMap::new, Collectors.toList())));
        List<PerformanceRow> out = new ArrayList<>();
        grouped.forEach((k, byHorizon) -> byHorizon.forEach((h, list) -> out.add(stats(dimension, k, h, list))));
        return out;
    }

    private static PerformanceRow stats(String dimension, String key, int horizon, List<SignalOutcomeEntity> list) {
        double meanNet = list.stream().mapToDouble(SignalOutcomeEntity::getNetReturn).average().orElse(Double.NaN);
        List<SignalOutcomeEntity> withExcess = list.stream().filter(o -> o.getExcessReturn() != null).toList();
        Double meanExcess = withExcess.isEmpty() ? null
                : withExcess.stream().mapToDouble(SignalOutcomeEntity::getExcessReturn).average().orElseThrow();
        List<SignalOutcomeEntity> directional = withExcess.stream()
                .filter(o -> "BUY".equals(o.getSignalType()) || "SELL".equals(o.getSignalType())).toList();
        long hits = directional.stream().filter(o -> "BUY".equals(o.getSignalType())
                ? o.getExcessReturn() > 0 : o.getExcessReturn() < 0).count();
        Double hitRate = directional.isEmpty() ? null : (double) hits / directional.size();
        return new PerformanceRow(dimension, key, horizon, list.size(), meanNet, meanExcess, hitRate,
                directional.size(), list.size() < MIN_SAMPLES);
    }

    static String confidenceBucket(double confidence) {
        double pct = confidence * 100;
        return pct >= 70 ? "≥70" : pct >= 40 ? "40–69" : "<40";
    }

    private static String orNotRecorded(String value) {
        return value == null || value.isBlank() ? NOT_RECORDED : value;
    }
}
