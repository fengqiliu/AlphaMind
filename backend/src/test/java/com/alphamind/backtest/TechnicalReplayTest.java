package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalReplayTest {
    private static final int WINDOW = 40;
    private static final Costs FREE = new Costs(0, 0);
    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    private static DailyKline bar(int index, double open, double close) {
        return new DailyKline(START.plusDays(index), open, close,
                Math.min(open, close) * 0.99, Math.max(open, close) * 1.01, 1000, 1000 * close, 1);
    }

    /** 收盘价序列，开盘 = 前一日收盘。 */
    private static List<DailyKline> series(double[] closes) {
        List<DailyKline> bars = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) bars.add(bar(i, i == 0 ? closes[0] : closes[i - 1], closes[i]));
        return bars;
    }

    private static double[] wave(int n) {
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) closes[i] = 20 + 2 * Math.sin(i / 4.0) + i * 0.02;
        return closes;
    }

    private static BacktestSample find(ReplayResult result, LocalDate date, int horizon) {
        return result.samples().stream()
                .filter(s -> s.signalDate().equals(date) && s.horizon() == horizon)
                .findFirst().orElse(null);
    }

    @Test
    void noLookahead() {
        double[] base = wave(150);
        double[] boom = new double[180], bust = new double[180];
        for (int i = 0; i < 180; i++) {
            boom[i] = i < 150 ? base[i] : base[149] * Math.pow(1.05, i - 149);
            bust[i] = i < 150 ? base[i] : base[149] * Math.pow(0.95, i - 149);
        }
        LocalDate day = START.plusDays(149);
        var up = find(TechnicalReplay.replay("600000", series(boom), WINDOW, new int[]{5}, FREE), day, 5);
        var down = find(TechnicalReplay.replay("600000", series(bust), WINDOW, new int[]{5}, FREE), day, 5);
        assertEquals(up.score(), down.score(), "t 日分数不得受 t 之后数据影响");
        assertTrue(up.grossReturn() > 0 && down.grossReturn() < 0);
    }

    @Test
    void entryIsNextOpenExitIsCloseAfterH() {
        var bars = series(wave(80));
        int t = 50;
        var sample = find(TechnicalReplay.replay("600000", bars, WINDOW, new int[]{5}, FREE), bars.get(t).tradeDate(), 5);
        assertEquals(bars.get(t + 5).close() / bars.get(t + 1).open() - 1, sample.grossReturn(), 1e-12);
    }

    @Test
    void limitUpNextDaySkipped() {
        var bars = new ArrayList<>(series(wave(80)));
        int t = 50;
        double up = bars.get(t).close() * 1.10;
        bars.set(t + 1, new DailyKline(bars.get(t + 1).tradeDate(), up, up, up, up, 10, 10 * up, 0.1));
        var main = TechnicalReplay.replay("600000", bars, WINDOW, new int[]{5}, FREE);
        assertNull(find(main, bars.get(t).tradeDate(), 5));
        assertEquals(1, main.limitUpSkipped());
        var chinext = TechnicalReplay.replay("300750", bars, WINDOW, new int[]{5}, FREE);
        assertNotNull(find(chinext, bars.get(t).tradeDate(), 5), "创业板涨停阈值为 19.5%");
        assertEquals(0, chinext.limitUpSkipped());
    }

    @Test
    void costsApplied() {
        double[] closes = new double[80];
        java.util.Arrays.fill(closes, 20);
        var sample = TechnicalReplay.replay("600000", series(closes), WINDOW, new int[]{5}, Costs.DEFAULT).samples().get(0);
        assertEquals(0, sample.grossReturn(), 1e-12);
        assertEquals((1 - 0.00125) / (1 + 0.00075) - 1, sample.netReturn(), 1e-12);
        assertEquals(-0.002, sample.netReturn(), 1e-4);
    }

    @Test
    void tailWithoutFullHoldingIsDropped() {
        var bars = series(wave(80));
        var result = TechnicalReplay.replay("600000", bars, WINDOW, new int[]{5, 20}, FREE);
        assertNull(find(result, bars.get(79 - 5 + 1).tradeDate(), 5));
        assertNotNull(find(result, bars.get(79 - 5).tradeDate(), 5));
        assertNull(find(result, bars.get(79 - 19).tradeDate(), 20));
        assertNotNull(find(result, bars.get(79 - 20).tradeDate(), 20));
    }

    @Test
    void firstSignalNeedsFullWindow() {
        var bars = series(wave(80));
        var result = TechnicalReplay.replay("600000", bars, WINDOW, new int[]{5}, FREE);
        LocalDate first = result.samples().stream().map(BacktestSample::signalDate).min(LocalDate::compareTo).orElseThrow();
        assertEquals(bars.get(WINDOW - 1).tradeDate(), first);
    }
}
