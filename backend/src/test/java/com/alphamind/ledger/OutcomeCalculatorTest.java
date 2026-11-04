package com.alphamind.ledger;

import com.alphamind.backtest.Costs;
import com.alphamind.market.provider.DailyKline;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutcomeCalculatorTest {
    private static final Costs FREE = new Costs(0, 0);

    /** 交易日从 2026-11-02（周一）开始，跳过周末；open 与 close 由序号决定，便于手算。 */
    private static List<DailyKline> bars(int n, double base) {
        List<DailyKline> out = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 11, 2);
        for (int i = 0; i < n; i++) {
            while (day.getDayOfWeek().getValue() >= 6) day = day.plusDays(1);
            double open = base + i, close = base + i + 0.5;
            out.add(new DailyKline(day, open, close, open - 1, close + 1, 100, 100 * close, 1));
            day = day.plusDays(1);
        }
        return out;
    }

    @Test
    void entersNextTradingDayOpenAndExitsAfterHBars() {
        var stock = bars(30, 10);
        var bench = bars(30, 100);
        // 信号日 = 第 0 根（11-02）；入场第 1 根开盘 11，出场第 5 根收盘 15.5
        var outcome = OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 2), stock, bench, 5, FREE).orElseThrow();

        assertEquals(stock.get(1).tradeDate(), outcome.entryDate());
        assertEquals(11.0, outcome.entryPrice());
        assertEquals(stock.get(5).tradeDate(), outcome.exitDate());
        assertEquals(15.5, outcome.exitPrice());
        assertEquals(15.5 / 11 - 1, outcome.grossReturn(), 1e-12);
        assertEquals(105.5 / 101 - 1, outcome.benchmarkReturn(), 1e-12);
        assertEquals(outcome.netReturn() - outcome.benchmarkReturn(), outcome.excessReturn(), 1e-12);
        assertFalse(outcome.entryBlocked());
    }

    @Test
    void weekendSignalUsesLastTradingDayBeforeIt() {
        var stock = bars(30, 10);
        // 11-07 是周六：信号日归到 11-06（第 4 根），入场第 5 根
        var outcome = OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 7), stock, stock, 5, FREE).orElseThrow();
        assertEquals(stock.get(5).tradeDate(), outcome.entryDate());
    }

    @Test
    void notMaturedYetReturnsEmpty() {
        var stock = bars(5, 10);
        assertTrue(OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 2), stock, stock, 5, FREE).isEmpty(),
                "需要第 5 根之后的数据，当前只有 0..4");
        assertTrue(OutcomeCalculator.compute("600519", LocalDate.of(2026, 10, 1), stock, stock, 1, FREE).isEmpty(),
                "信号日早于所有 K 线时无法定位");
    }

    @Test
    void costsAreDeductedFromNetOnly() {
        var stock = bars(30, 10);
        var outcome = OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 2), stock, stock, 5, Costs.DEFAULT).orElseThrow();
        assertEquals(Costs.DEFAULT.net(11, 15.5), outcome.netReturn(), 1e-12);
        assertTrue(outcome.netReturn() < outcome.grossReturn());
    }

    @Test
    void missingBenchmarkBarsLeaveBenchmarkAndExcessNull() {
        var stock = bars(30, 10);
        var outcome = OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 2), stock, List.of(), 5, FREE).orElseThrow();
        assertNull(outcome.benchmarkReturn());
        assertNull(outcome.excessReturn());
    }

    @Test
    void limitUpEntryIsFlaggedBlocked() {
        var stock = new ArrayList<>(bars(30, 10));
        var t1 = stock.get(1);
        double up = stock.get(0).close() * 1.10;
        stock.set(1, new DailyKline(t1.tradeDate(), up, up, up, up, 10, 10 * up, 0.1));
        var outcome = OutcomeCalculator.compute("600519", LocalDate.of(2026, 11, 2), stock, stock, 5, FREE).orElseThrow();
        assertTrue(outcome.entryBlocked(), "一字涨停无法买入，计入账本但不参与统计");
    }
}
