package com.alphamind.scoring;

import com.alphamind.model.dto.TechnicalIndicatorsDTO;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术规则 V1 的唯一实现：线上 {@code TechnicalAgent} 与离线回测共用，保证口径一致。
 * 无状态、不调用 LLM、不访问网络。
 */
public final class TechnicalScoring {

    /** 技术规则 V1 的回测结论，见 docs/回测/技术分回测-2026-10-26.md。 */
    public static final String BACKTEST_FINDING =
            "回测（2026-10-26，72只观察股约5年，样本外持有10日）未见区分力：≥70分档超额收益不高于<40分档，IC未达显著；"
                    + "技术分只描述当前指标状态，不应单独作为买卖依据。";

    /** 附在每条交易信号理由后的说明：技术分回测未见区分力，舆情分无法回测。 */
    public static final String SIGNAL_CAVEAT =
            "技术分经历史回测未见区分力，舆情分缺少历史新闻无法回测，本信号整体未经验证，仅作规则参考。";

    private TechnicalScoring() {}

    /**
     * 从日 K 线（[open, close, low, high]）与现价计算技术指标和技术分
     * MACD: EMA12/EMA26 → DIF, EMA9(DIF) → DEA, 柱 = (DIF-DEA)*2
     * RSI:  Wilder 平滑法 RSI(6/12/24)
     * KDJ:  9 日最高/最低 → RSV → K/D/J
     * 布林带: MA20 ± 2σ
     */
    public static TechnicalIndicatorsDTO score(List<double[]> klines, double price) {

        if (!Double.isFinite(price) || price <= 0 || klines == null || klines.size() < 34) {
            throw new IllegalArgumentException("技术分析至少需要34根有效日K线和有效报价，数据不足时不构造近似行情");
        }
        int n = klines.size();
        double[] closes = new double[n], highs = new double[n], lows = new double[n];
        for (int i = 0; i < n; i++) {
            double[] bar = klines.get(i);
            if (bar == null || bar.length < 4 || Arrays.stream(bar).anyMatch(v -> !Double.isFinite(v) || v <= 0)
                    || bar[2] > Math.min(bar[0], bar[1]) || bar[3] < Math.max(bar[0], bar[1]) || bar[2] > bar[3]) {
                throw new IllegalArgumentException("日K线包含无效OHLC，停止技术评分");
            }
            closes[i] = bar[1]; lows[i] = bar[2]; highs[i] = bar[3];
        }

        // ── MACD (EMA12 / EMA26 / EMA9) ──────────────
        double[] ema12 = ema(closes, 12, 0);
        double[] ema26 = ema(closes, 26, 0);
        int last = closes.length - 1;
        double[] difArr = new double[closes.length];
        for (int i = 0; i < closes.length; i++) difArr[i] = ema12[i] - ema26[i];
        double[] deaArr = ema(difArr, 9, 25);
        double difVal  = r3(difArr[last]);
        double deaVal  = r3(deaArr[last]);
        double histVal = r3((difArr[last] - deaArr[last]) * 2);

        // ── RSI (Wilder) ──────────────────────────────
        double rsi6  = r2(rsi(closes, 6));
        double rsi12 = r2(rsi(closes, 12));
        double rsi24 = r2(rsi(closes, 24));

        // ── KDJ (9-period Stochastic) ─────────────────
        double[] kdjArr = kdj(closes, highs, lows, 9);
        double kVal = r2(kdjArr[0]);
        double dVal = r2(kdjArr[1]);
        double jVal = r2(kdjArr[2]);

        // ── Bollinger Bands (MA20 ± 2σ) ──────────────
        double[] boll = bollinger(closes, 20);
        double bolUpper  = r2(boll[0]);
        double bolMiddle = r2(boll[1]);
        double bolLower  = r2(boll[2]);

        // ── 综合技术评分 ───────────────────────────────
        Map<String, Integer> contributions = new LinkedHashMap<>();
        contributions.put("基础分", 50);
        contributions.put("MACD柱", histVal > 0 ? 15 : histVal < 0 ? -10 : 0);
        contributions.put("RSI12", rsi12 > 50 && rsi12 < 70 ? 10 : rsi12 < 30 ? 15 : rsi12 > 70 ? -10 : 0);
        contributions.put("KDJ_J", jVal < 20 ? 10 : jVal > 90 ? -8 : 0);
        contributions.put("布林位置", price < bolLower ? 8 : price > bolUpper ? -8 : 0);
        int rawScore = contributions.values().stream().mapToInt(Integer::intValue).sum();
        int score = Math.max(10, Math.min(95, rawScore));
        contributions.put("限幅调整", score - rawScore);
        String scoreExplanation = "技术规则 V1：基础50分加MACD/RSI12/KDJ/布林位置贡献，限于10–95分；>=70偏多、<40偏空，其余中性。"
                + "超卖与跌破下轨是反转假设加分，不是已发生反弹；仅当前指标关系，未证明金叉/死叉、持续变化或同业排名。使用"
                + n + "根有效日K线。" + BACKTEST_FINDING;

        return TechnicalIndicatorsDTO.builder()
                .macd(TechnicalIndicatorsDTO.MacdDTO.builder()
                        .dif(difVal).dea(deaVal).histogram(histVal).build())
                .rsi(TechnicalIndicatorsDTO.RsiDTO.builder()
                        .rsi6(rsi6).rsi12(rsi12).rsi24(rsi24).build())
                .kdj(TechnicalIndicatorsDTO.KdjDTO.builder()
                        .k(kVal).d(dVal).j(jVal).build())
                .bollinger(TechnicalIndicatorsDTO.BollingerDTO.builder()
                        .upper(bolUpper).middle(bolMiddle).lower(bolLower).build())
                .technicalScore(score)
                .sampleSize(n)
                .scoreExplanation(scoreExplanation)
                .scoreContributions(contributions)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 技术指标计算工具方法
    // ─────────────────────────────────────────────────────────

    /** 指数移动平均 (EMA)：初始值取前 period 个收盘价的 SMA */
    private static double[] ema(double[] data, int period, int start) {
        double[] result = new double[data.length];
        Arrays.fill(result, Double.NaN);
        int seed = start + period - 1;
        double total = 0;
        for (int i = start; i <= seed; i++) total += data[i];
        result[seed] = total / period;
        double multiplier = 2.0 / (period + 1);
        for (int i = seed + 1; i < data.length; i++)
            result[i] = data[i] * multiplier + result[i - 1] * (1 - multiplier);
        return result;
    }

    /** Wilder 平滑法 RSI */
    private static double rsi(double[] closes, int period) {
        if (closes.length < period + 1) return 50.0;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff > 0) avgGain += diff; else avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < closes.length; i++) {
            double diff = closes[i] - closes[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
        }
        if (avgLoss == 0) return avgGain == 0 ? 50.0 : 100.0;
        return 100.0 - 100.0 / (1 + avgGain / avgLoss);
    }

    /** 9 日随机指标 KDJ */
    private static double[] kdj(double[] closes, double[] highs, double[] lows, int period) {
        double k = 50, d = 50;
        for (int i = period - 1; i < closes.length; i++) {
            double hh = highs[i], ll = lows[i];
            for (int j = i - period + 1; j < i; j++) {
                hh = Math.max(hh, highs[j]);
                ll = Math.min(ll, lows[j]);
            }
            double rsv = (hh == ll) ? 50 : (closes[i] - ll) / (hh - ll) * 100;
            k = 2.0 / 3.0 * k + 1.0 / 3.0 * rsv;
            d = 2.0 / 3.0 * d + 1.0 / 3.0 * k;
        }
        return new double[]{k, d, 3 * k - 2 * d};
    }

    /** MA(period) 布林带，返回 [upper, middle, lower] */
    private static double[] bollinger(double[] closes, int period) {
        int n = closes.length;
        if (n < period) period = n;
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += closes[i];
        double mean = sum / period;
        double var = 0;
        for (int i = n - period; i < n; i++) {
            double diff = closes[i] - mean;
            var += diff * diff;
        }
        double sigma = Math.sqrt(var / period);
        return new double[]{mean + 2 * sigma, mean, mean - 2 * sigma};
    }

    private static double r2(double v) { return Math.round(v * 100) / 100.0; }
    private static double r3(double v) { return Math.round(v * 1000) / 1000.0; }
}
