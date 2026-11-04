package com.alphamind.ledger;

import com.alphamind.backtest.Costs;
import com.alphamind.backtest.TechnicalReplay;
import com.alphamind.market.provider.DailyKline;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 信号事后结果的纯计算，口径与技术分回测一致：信号日 t 为报告生成日（非交易日归到之前最近的交易日），
 * t 之后第一个交易日开盘买入，第 t+h 根 K 线收盘卖出。基准收益取同一入场日开盘到出场日收盘。
 */
public final class OutcomeCalculator {

    public record Outcome(LocalDate entryDate, double entryPrice, LocalDate exitDate, double exitPrice,
                          double grossReturn, double netReturn, Double benchmarkReturn, Double excessReturn,
                          boolean entryBlocked) {}

    private OutcomeCalculator() {}

    /** 数据尚未覆盖到出场日、或信号日早于全部 K 线时返回 empty（尚未到期 / 无法定位）。 */
    public static Optional<Outcome> compute(String stockCode, LocalDate signalDate, List<DailyKline> bars,
                                            List<DailyKline> benchmark, int horizon, Costs costs) {
        int t = lastIndexOnOrBefore(bars, signalDate);
        if (t < 0 || t + horizon >= bars.size()) {
            return Optional.empty();
        }
        DailyKline entry = bars.get(t + 1);
        DailyKline exit = bars.get(t + horizon);
        boolean blocked = TechnicalReplay.isLimitUpLocked(entry, bars.get(t).close(),
                TechnicalReplay.limitUpThreshold(stockCode));
        double gross = exit.close() / entry.open() - 1;
        double net = costs.net(entry.open(), exit.close());

        Double benchReturn = null;
        DailyKline benchEntry = onDate(benchmark, entry.tradeDate());
        DailyKline benchExit = onDate(benchmark, exit.tradeDate());
        if (benchEntry != null && benchExit != null) {
            benchReturn = benchExit.close() / benchEntry.open() - 1;
        }
        return Optional.of(new Outcome(entry.tradeDate(), entry.open(), exit.tradeDate(), exit.close(),
                gross, net, benchReturn, benchReturn == null ? null : net - benchReturn, blocked));
    }

    private static int lastIndexOnOrBefore(List<DailyKline> bars, LocalDate date) {
        int found = -1;
        for (int i = 0; i < bars.size() && !bars.get(i).tradeDate().isAfter(date); i++) {
            found = i;
        }
        return found;
    }

    private static DailyKline onDate(List<DailyKline> bars, LocalDate date) {
        for (DailyKline bar : bars) {
            if (bar.tradeDate().equals(date)) return bar;
        }
        return null;
    }
}
