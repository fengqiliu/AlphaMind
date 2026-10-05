package com.alphamind.news.service;

import com.alphamind.news.provider.NewsArticle;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于真实新闻标题的确定性情绪信号。
 *
 * <p>对每条标题按固定关键词表判断利好/利空（一条标题只计入先命中的一侧，避免重复计分），
 * 从 0.5 基准出发每命中一篇 ±0.06，最终收敛到 [0.2, 0.8]。信号只通过小权重混合进舆情评分
 * （见 {@code SentimentAgent}），命中的关键词随利好/利空因素透出，保证评分可解释、可复核。
 * 这里不调用 LLM、不做随机估计——没有真实标题就没有信号。
 */
public final class HeadlineSentimentScorer {

    /** 单条标题的情绪贡献幅度。 */
    private static final double HIT_STEP = 0.06;
    private static final double SIGNAL_FLOOR = 0.2;
    private static final double SIGNAL_CEILING = 0.8;

    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "预增", "预盈", "扭亏", "中标", "回购", "增持", "分红", "派息",
            "获批", "签订", "净流入", "创新高", "上调评级", "买入评级", "涨停");

    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "预亏", "亏损", "下滑", "减持", "质押", "诉讼", "立案", "调查", "处罚",
            "违规", "解禁", "净流出", "下调评级", "卖出评级", "跌停", "退市", "警示函");

    private HeadlineSentimentScorer() {
    }

    /**
     * 评估结果。{@code score} 位于 [0.2, 0.8]，0.5 为中性；
     * 命中的关键词列表按命中顺序去重，供评分说明使用。
     */
    public record HeadlineSignal(double score, int positiveHits, int negativeHits,
                                 List<String> positiveKeywords, List<String> negativeKeywords) {
    }

    public static HeadlineSignal evaluate(List<NewsArticle> articles) {
        double score = 0.5;
        int positiveHits = 0;
        int negativeHits = 0;
        List<String> positiveKeywords = new ArrayList<>();
        List<String> negativeKeywords = new ArrayList<>();

        if (articles != null) {
            for (NewsArticle article : articles) {
                String title = article == null ? null : article.title();
                if (title == null || title.isBlank()) {
                    continue;
                }
                String positiveKeyword = firstMatch(title, POSITIVE_KEYWORDS);
                if (positiveKeyword != null) {
                    positiveHits++;
                    score += HIT_STEP;
                    if (!positiveKeywords.contains(positiveKeyword)) {
                        positiveKeywords.add(positiveKeyword);
                    }
                    continue;
                }
                String negativeKeyword = firstMatch(title, NEGATIVE_KEYWORDS);
                if (negativeKeyword != null) {
                    negativeHits++;
                    score -= HIT_STEP;
                    if (!negativeKeywords.contains(negativeKeyword)) {
                        negativeKeywords.add(negativeKeyword);
                    }
                }
            }
        }

        double clamped = Math.max(SIGNAL_FLOOR, Math.min(SIGNAL_CEILING, score));
        return new HeadlineSignal(
                Math.round(clamped * 100) / 100.0,
                positiveHits, negativeHits,
                List.copyOf(positiveKeywords), List.copyOf(negativeKeywords));
    }

    private static String firstMatch(String title, List<String> keywords) {
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }
}
