package com.alphamind.scoring;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.DebatePosition;
import java.util.*;

/** 三方共享证据边界，立场只能筛选论据，不能生成未经提供的事实。 */
public final class DebateEvidence {
    private DebateEvidence() {}

    public static List<String> reasons(MarketDataDTO market, TechnicalIndicatorsDTO tech,
                                       SentimentDataDTO sentiment, DebatePosition position) {
        List<String> reasons = new ArrayList<>();
        boolean bull = position == DebatePosition.BULLISH, bear = position == DebatePosition.BEARISH;
        if (tech != null && tech.getTechnicalScore() != null) {
            reasons.add("技术规则评分 " + tech.getTechnicalScore() + "/100；不是同业排名或收益概率");
            if (tech.getMacd() != null && tech.getMacd().getHistogram() != null) {
                double value = tech.getMacd().getHistogram();
                if ((!bull || value > 0) && (!bear || value < 0))
                    reasons.add(String.format(Locale.ROOT, "当前MACD柱 %.3f；单点关系不证明交叉或持续变化", value));
            }
            if (tech.getRsi() != null && tech.getRsi().getRsi12() != null) {
                double value = tech.getRsi().getRsi12();
                if ((!bull || value < 70) && (!bear || value > 70))
                    reasons.add(String.format(Locale.ROOT, "当前RSI12 %.2f；超买/超卖不保证回调或反弹", value));
            }
            if (tech.getKdj() != null && tech.getKdj().getK() != null && tech.getKdj().getD() != null) {
                double k = tech.getKdj().getK(), d = tech.getKdj().getD();
                if ((!bull || k > d) && (!bear || k < d))
                    reasons.add(String.format(Locale.ROOT, "当前K %.2f、D %.2f；未验证此前交叉", k, d));
            }
        } else reasons.add("技术数据未提供，无法核实技术论点");
        if (market != null) {
            Double pe = market.getPe();
            if (pe == null || !Double.isFinite(pe) || pe == 0) reasons.add("PE未提供或无效，不能判断估值合理、低估或亏损");
            else if (pe < 0) reasons.add("PE为负，不适用正PE比较，需核对盈利口径");
            else reasons.add(String.format(Locale.ROOT, "PE %.1f，缺少行业与历史分位，不能据此认定估值优势", pe));
            if (market.getChangePercent() != null)
                reasons.add(String.format(Locale.ROOT, "本次报价涨跌幅 %+.2f%%，不代表一段时期累计表现", market.getChangePercent()));
        }
        if (sentiment != null && sentiment.getSentimentScore() != null) {
            reasons.add(String.format(Locale.ROOT, "行情与新闻代理评分 %.0f/100，不代表机构观点或社会情绪实测", sentiment.getSentimentScore() * 100));
            List<String> factors = bull ? sentiment.getPositiveFactors() : bear ? sentiment.getNegativeFactors() : null;
            if (factors != null) factors.stream().limit(2).forEach(value -> reasons.add("已有代理因素：" + value));
            if (sentiment.getDataGaps() != null) reasons.add("数据缺口：" + String.join("；", sentiment.getDataGaps()));
        }
        reasons.add("财报、行业比较及盈利预测未在本轮核实，立场不能补足缺失证据");
        return reasons;
    }

    public static String render(MarketDataDTO market, TechnicalIndicatorsDTO tech,
                                SentimentDataDTO sentiment, DebatePosition position) {
        return "**【" + position.getLabel() + "观点】基于现有证据检验假设**\n\n"
                + String.join("\n", reasons(market, tech, sentiment, position).stream().map(s -> "- " + s).toList())
                + "\n\n媒体关注度缺少实测来源，不据此判断泡沫风险。"
                + "\n观点不生成独立预测目标价或固定止损；策略交易计划另列，未经回测校准。";
    }

    public static String prompt(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment,
                                DebatePosition position, String question) {
        return "标的：" + (market == null ? "未提供" : market.getStockCode() + " " + market.getStockName())
                + "\n" + render(market, tech, sentiment, position)
                + "\n技术口径：" + (tech == null ? "未提供" : tech.getScoreExplanation())
                + "\n规则一致度：" + AnalysisScoringPolicy.confidence(market, tech, sentiment).getExplanation()
                + "\n用户问题：" + question;
    }
}
