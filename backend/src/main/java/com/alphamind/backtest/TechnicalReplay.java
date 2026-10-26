package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.scoring.TechnicalScoring;

import java.util.ArrayList;
import java.util.List;

/**
 * 逐日回放技术分，严格时间对齐：t 日只使用截至 t 的最近 {@code window} 根 K 线、现价取 t 日收盘；
 * 交易在 t+1 开盘买入、t+h 收盘卖出（h 按该股后续第 h 根 K 线计，停牌日自然跳过）。
 */
public final class TechnicalReplay {

    private TechnicalReplay() {}

    public static ReplayResult replay(String code, List<DailyKline> bars, int window, int[] horizons, Costs costs) {
        List<BacktestSample> samples = new ArrayList<>();
        int limitUpSkipped = 0;
        int n = bars.size();
        double limit = limitUpThreshold(code);
        for (int t = window - 1; t <= n - 2; t++) {
            DailyKline signal = bars.get(t);
            DailyKline entryBar = bars.get(t + 1);
            if (isLimitUpLocked(entryBar, signal.close(), limit)) {
                limitUpSkipped++;
                continue;
            }
            int score;
            try {
                score = TechnicalScoring.score(toArrays(bars.subList(t - window + 1, t + 1)), signal.close()).getTechnicalScore();
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            double entry = entryBar.open();
            for (int h : horizons) {
                if (t + h >= n) continue;
                double exit = bars.get(t + h).close();
                samples.add(new BacktestSample(code, signal.tradeDate(), score, h, exit / entry - 1, costs.net(entry, exit)));
            }
        }
        return new ReplayResult(samples, limitUpSkipped);
    }

    /** 主板 10%（阈值 9.5%）；创业板/科创板 20%（阈值 19.5%）。 */
    static double limitUpThreshold(String code) {
        return code.startsWith("300") || code.startsWith("301") || code.startsWith("688") || code.startsWith("689")
                ? 0.195 : 0.095;
    }

    private static boolean isLimitUpLocked(DailyKline bar, double previousClose, double threshold) {
        boolean oneWord = bar.open() == bar.high() && bar.high() == bar.low() && bar.low() == bar.close();
        return oneWord && bar.close() / previousClose - 1 >= threshold;
    }

    private static List<double[]> toArrays(List<DailyKline> bars) {
        List<double[]> result = new ArrayList<>(bars.size());
        for (DailyKline bar : bars) result.add(new double[]{bar.open(), bar.close(), bar.low(), bar.high()});
        return result;
    }
}
