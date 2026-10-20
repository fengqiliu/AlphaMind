package com.alphamind.scoring;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import java.util.Locale;

/** 明确量纲与规则一致性；所有阈值是工程规则，未经收益概率校准。 */
public final class AnalysisScoringPolicy {
    public static final String METHOD = "RULE_AGREEMENT_V1";
    private AnalysisScoringPolicy() {}

    public static double composite(int technical, double sentiment) {
        if (technical < 0 || technical > 100) throw new IllegalArgumentException("技术评分必须在0–100之间");
        unit(sentiment);
        return technical * 0.5 + sentiment * 100 * 0.5;
    }

    public static DebatePosition technicalPosition(int score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException("技术评分必须在0–100之间");
        return score >= 70 ? DebatePosition.BULLISH : score < 40 ? DebatePosition.BEARISH : DebatePosition.NEUTRAL;
    }

    public static ConfidenceDTO confidence(MarketDataDTO market, TechnicalIndicatorsDTO technical, SentimentDataDTO sentiment) {
        if (technical == null || technical.getTechnicalScore() == null || sentiment == null || sentiment.getSentimentScore() == null) {
            return ConfidenceDTO.builder().value(0.0).level(ConfidenceLevel.LOW).method(METHOD)
                    .explanation("缺少技术或舆情信号，规则一致度为0；不是收益概率。").build();
        }
        double t = technical.getTechnicalScore() / 100.0;
        double s = sentiment.getSentimentScore();
        double combined = composite(technical.getTechnicalScore(), s) / 100;
        double agreement = 1 - Math.abs(t - s);
        double strength = 2 * Math.abs(combined - 0.5);
        double raw = agreement * (0.5 + 0.5 * strength);
        double coverage = sentiment.getSignalCoverage() == null ? 0.4 : sentiment.getSignalCoverage();
        unit(coverage);
        double ceiling = coverage;
        if (technical.getSampleSize() == null || technical.getSampleSize() < 34) ceiling = Math.min(ceiling, 0.4);
        if (market == null || Boolean.TRUE.equals(market.getStale())) ceiling = Math.min(ceiling, 0.5);
        if (Boolean.TRUE.equals(sentiment.getNewsStale())) ceiling = Math.min(ceiling, 0.6);
        double value = Math.round(Math.min(raw, ceiling) * 100) / 100.0;
        return ConfidenceDTO.builder().value(value).level(ConfidenceLevel.fromValue(value)).method(METHOD)
                .explanation(String.format(Locale.ROOT,
                        "规则一致度 V1：技术%.0f/100、舆情%.0f/100，等权综合%.1f/100；方向一致性%.2f、偏离中性强度%.2f，原始值%.2f；信号覆盖%.0f%%，数据/缓存上限%.2f。无策略仓位加分，非胜率或收益概率，未经回测校准。",
                        t * 100, s * 100, combined * 100, agreement, strength, raw, coverage * 100, ceiling)).build();
    }

    public static String describe(ConfidenceDTO confidence) {
        if (confidence == null || confidence.getValue() == null) return "未记录置信度";
        boolean current = METHOD.equals(confidence.getMethod());
        return String.format(Locale.ROOT, "%s %.0f%s；非收益概率",
                current ? "规则一致度" : "历史置信度（方法未记录）", confidence.getValue() * 100, current ? "/100" : "%");
    }

    private static void unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("归一化信号必须在0–1之间");
    }
}
