package com.alphamind.news.service;

import com.alphamind.news.provider.NewsArticle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeadlineSentimentScorerTest {

    private static NewsArticle article(String title) {
        return new NewsArticle(title, null, null);
    }

    @Test
    void positiveHeadlinesRaiseScoreAboveNeutral() {
        HeadlineSentimentScorer.HeadlineSignal signal = HeadlineSentimentScorer.evaluate(List.of(
                article("公司中标电网改造项目"),
                article("拟以2亿元回购股份"),
                article("前三季度业绩预增50%")));

        assertTrue(signal.score() > 0.5);
        assertEquals(3, signal.positiveHits());
        assertEquals(0, signal.negativeHits());
        assertTrue(signal.positiveKeywords().contains("中标"));
        assertTrue(signal.positiveKeywords().contains("回购"));
        assertTrue(signal.positiveKeywords().contains("预增"));
    }

    @Test
    void negativeHeadlinesLowerScoreBelowNeutral() {
        HeadlineSentimentScorer.HeadlineSignal signal = HeadlineSentimentScorer.evaluate(List.of(
                article("股东计划减持不超过2%"),
                article("公司收到警示函"),
                article("主营业务收入同比下滑")));

        assertTrue(signal.score() < 0.5);
        assertEquals(0, signal.positiveHits());
        assertEquals(3, signal.negativeHits());
        assertTrue(signal.negativeKeywords().contains("减持"));
        assertTrue(signal.negativeKeywords().contains("警示函"));
        assertTrue(signal.negativeKeywords().contains("下滑"));
    }

    @Test
    void mixedHeadlinesOffsetEachOther() {
        HeadlineSentimentScorer.HeadlineSignal positive = HeadlineSentimentScorer.evaluate(
                List.of(article("业绩预增"), article("业绩预增"), article("收到处罚通知")));
        assertTrue(positive.score() > 0.5);
        assertEquals(2, positive.positiveHits());
        assertEquals(1, positive.negativeHits());
    }

    @Test
    void headlineCountsOnceEvenWhenMatchingBothSides() {
        HeadlineSentimentScorer.HeadlineSignal signal = HeadlineSentimentScorer.evaluate(
                List.of(article("业绩预增但股东减持")));

        assertEquals(1, signal.positiveHits());
        assertEquals(0, signal.negativeHits());
    }

    @Test
    void blankOrMissingTitlesAreIgnoredAndSignalStaysNeutral() {
        HeadlineSentimentScorer.HeadlineSignal signal = HeadlineSentimentScorer.evaluate(java.util.Arrays.asList(
                article(""),
                article(null),
                null));

        assertEquals(0.5, signal.score());
        assertEquals(0, signal.positiveHits());
        assertEquals(0, signal.negativeHits());

        assertEquals(0.5, HeadlineSentimentScorer.evaluate(null).score());
    }

    @Test
    void scoreIsClampedWithinBoundedRange() {
        HeadlineSentimentScorer.HeadlineSignal veryNegative = HeadlineSentimentScorer.evaluate(
                java.util.stream.IntStream.range(0, 30)
                        .mapToObj(i -> article("公司预亏并遭立案调查"))
                        .toList());
        assertEquals(0.2, veryNegative.score());

        HeadlineSentimentScorer.HeadlineSignal veryPositive = HeadlineSentimentScorer.evaluate(
                java.util.stream.IntStream.range(0, 30)
                        .mapToObj(i -> article("公司中标并上调评级"))
                        .toList());
        assertEquals(0.8, veryPositive.score());
    }
}
