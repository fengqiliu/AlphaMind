package com.alphamind.ledger;

import com.alphamind.backtest.Costs;
import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.EastmoneyKlineMarketDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * 信号账本：为已到期的报告补算 5/10/20 个交易日后的实际收益与沪深 300 超额收益。
 * 每只股票、基准各取一次 K 线；单只股票取数失败只跳过该股票，下次运行再补。
 */
@Slf4j
@Service
public class SignalLedgerService {

    public static final int[] HORIZONS = {5, 10, 20};
    public static final int HORIZON_COUNT = 3;
    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    static final int MAX_BARS = 1250;
    /** 自然日换算交易日时留的余量（节假日、出场日之后的数据）。 */
    static final int BAR_BUFFER = 40;

    public record Summary(int written, int pending, List<String> failedStocks) {}

    private final SignalOutcomeRepository outcomes;
    private final BiFunction<String, Integer, Optional<List<DailyKline>>> stockBars;
    private final IntFunction<Optional<List<DailyKline>>> benchmarkBars;
    private final Clock clock;

    @Autowired
    public SignalLedgerService(SignalOutcomeRepository outcomes, EastmoneyKlineMarketDataProvider provider) {
        this(outcomes, provider::fetchDailyKlines, provider::fetchBenchmarkKlines, Clock.systemUTC());
    }

    SignalLedgerService(SignalOutcomeRepository outcomes,
                        BiFunction<String, Integer, Optional<List<DailyKline>>> stockBars,
                        IntFunction<Optional<List<DailyKline>>> benchmarkBars,
                        Clock clock) {
        this.outcomes = outcomes;
        this.stockBars = stockBars;
        this.benchmarkBars = benchmarkBars;
        this.clock = clock;
    }

    public Summary evaluatePending() {
        List<LedgerCandidate> candidates = outcomes.findPendingCandidates();
        if (candidates.isEmpty()) {
            return new Summary(0, 0, List.of());
        }
        Map<String, Set<Integer>> done = outcomes.findByReportIdIn(
                        candidates.stream().map(LedgerCandidate::getId).toList()).stream()
                .collect(Collectors.groupingBy(SignalOutcomeEntity::getReportId,
                        Collectors.mapping(SignalOutcomeEntity::getHorizonDays, Collectors.toSet())));

        LocalDate today = LocalDate.now(clock.withZone(MARKET_ZONE));
        LocalDate earliest = candidates.stream().map(c -> signalDate(c.createdAt())).min(LocalDate::compareTo).orElse(today);
        List<DailyKline> benchmark = benchmarkBars.apply(barsNeeded(earliest, today)).orElse(List.of());

        int written = 0, pending = 0;
        List<String> failed = new ArrayList<>();
        Map<String, List<LedgerCandidate>> byStock = candidates.stream()
                .collect(Collectors.groupingBy(LedgerCandidate::getStockCode, LinkedHashMap::new, Collectors.toList()));
        for (var entry : byStock.entrySet()) {
            String code = entry.getKey();
            LocalDate first = entry.getValue().stream().map(c -> signalDate(c.createdAt())).min(LocalDate::compareTo).orElse(today);
            List<DailyKline> bars;
            try {
                bars = stockBars.apply(code, barsNeeded(first, today)).orElse(null);
            } catch (RuntimeException e) {
                log.warn("信号账本取数失败，下次再补: stockCode={}, reason={}", code, e.getMessage());
                bars = null;
            }
            if (bars == null || bars.isEmpty()) {
                failed.add(code);
                continue;
            }
            for (LedgerCandidate candidate : entry.getValue()) {
                Set<Integer> existing = done.getOrDefault(candidate.getId(), Set.of());
                for (int horizon : HORIZONS) {
                    if (existing.contains(horizon)) continue;
                    var outcome = OutcomeCalculator.compute(code, signalDate(candidate.createdAt()), bars,
                            benchmark, horizon, Costs.DEFAULT);
                    if (outcome.isEmpty()) {
                        pending++;
                        continue;
                    }
                    outcomes.save(toEntity(candidate, horizon, outcome.get()));
                    written++;
                }
            }
        }
        return new Summary(written, pending, List.copyOf(failed));
    }

    static LocalDate signalDate(Instant createdAt) {
        return createdAt.atZone(MARKET_ZONE).toLocalDate();
    }

    private static int barsNeeded(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return (int) Math.min(MAX_BARS, Math.max(0, days) + BAR_BUFFER);
    }

    private SignalOutcomeEntity toEntity(LedgerCandidate c, int horizon, OutcomeCalculator.Outcome o) {
        return SignalOutcomeEntity.builder()
                .reportId(c.getId())
                .stockCode(c.getStockCode())
                .horizonDays(horizon)
                .signalType(c.getSignalType())
                .confidenceValue(c.getConfidenceValue())
                .strategy(c.getStrategy())
                .analysisMode(c.getAnalysisMode())
                .personalLevel(c.getPersonalLevel())
                .signalDate(signalDate(c.createdAt()))
                .entryDate(o.entryDate())
                .entryPrice(o.entryPrice())
                .exitDate(o.exitDate())
                .exitPrice(o.exitPrice())
                .grossReturn(o.grossReturn())
                .netReturn(o.netReturn())
                .benchmarkReturn(o.benchmarkReturn())
                .excessReturn(o.excessReturn())
                .entryBlocked(o.entryBlocked())
                .evaluatedAt(OffsetDateTime.now(clock))
                .build();
    }
}
