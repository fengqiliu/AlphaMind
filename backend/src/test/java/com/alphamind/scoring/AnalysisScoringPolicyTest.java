package com.alphamind.scoring;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisScoringPolicyTest {
    private ConfidenceDTO confidence(int t, double s, double coverage, boolean stale) {
        return AnalysisScoringPolicy.confidence(MarketDataDTO.builder().stale(stale).build(),
                TechnicalIndicatorsDTO.builder().technicalScore(t).sampleSize(120).build(),
                SentimentDataDTO.builder().sentimentScore(s).signalCoverage(coverage).build());
    }

    @Test void compositeUsesSameScaleAndReachesBothEndpoints() {
        assertEquals(0, AnalysisScoringPolicy.composite(0, 0));
        assertEquals(100, AnalysisScoringPolicy.composite(100, 1));
        assertEquals(50, AnalysisScoringPolicy.composite(50, 0.5));
        assertEquals(70, AnalysisScoringPolicy.composite(60, 0.8));
        assertThrows(IllegalArgumentException.class, () -> AnalysisScoringPolicy.composite(101, 0.5));
        assertThrows(IllegalArgumentException.class, () -> AnalysisScoringPolicy.composite(50, Double.NaN));
    }

    @Test void agreementIsDirectionSymmetricAndDisagreementReducesIt() {
        assertEquals(0.9, confidence(90, 0.9, 1, false).getValue());
        assertEquals(0.9, confidence(10, 0.1, 1, false).getValue());
        assertEquals(0.5, confidence(50, 0.5, 1, false).getValue());
        assertEquals(0.1, confidence(90, 0.1, 1, false).getValue());
        assertEquals(AnalysisScoringPolicy.METHOD, confidence(90, 0.9, 1, false).getMethod());
    }

    @Test void incompleteAndStaleInputsCapConfidence() {
        assertEquals(0.66, confidence(90, 0.9, 0.66, false).getValue());
        assertEquals(0.5, confidence(90, 0.9, 1, true).getValue());
        var result = AnalysisScoringPolicy.confidence(MarketDataDTO.builder().build(),
                TechnicalIndicatorsDTO.builder().technicalScore(90).sampleSize(120).build(),
                SentimentDataDTO.builder().sentimentScore(0.9).signalCoverage(1.0).newsStale(true).build());
        assertEquals(0.6, result.getValue());
        assertEquals(0.4, AnalysisScoringPolicy.confidence(MarketDataDTO.builder().build(),
                TechnicalIndicatorsDTO.builder().technicalScore(90).build(),
                SentimentDataDTO.builder().sentimentScore(0.9).build()).getValue());
        assertEquals(0, AnalysisScoringPolicy.confidence(null, null, null).getValue());
        assertEquals(0, confidence(90, 0.9, 0, false).getValue());
    }

    @Test void legacyValuesAreNotRelabeledAsCurrentRules() {
        assertTrue(AnalysisScoringPolicy.describe(ConfidenceDTO.builder().value(0.8).build()).contains("历史置信度"));
        assertTrue(AnalysisScoringPolicy.describe(confidence(90,0.9,1,false)).contains("规则一致度 90/100"));
    }

    @Test void displayedLevelMatchesExactBoundariesAndTechnicalBuckets() {
        assertEquals(ConfidenceLevel.LOW, ConfidenceLevel.fromValue(0.399));
        assertEquals(ConfidenceLevel.MEDIUM, ConfidenceLevel.fromValue(0.4));
        assertEquals(ConfidenceLevel.HIGH, ConfidenceLevel.fromValue(0.7));
        assertEquals(DebatePosition.NEUTRAL, AnalysisScoringPolicy.technicalPosition(69));
        assertEquals(DebatePosition.BULLISH, AnalysisScoringPolicy.technicalPosition(70));
        assertEquals(DebatePosition.NEUTRAL, AnalysisScoringPolicy.technicalPosition(40));
        assertEquals(DebatePosition.BEARISH, AnalysisScoringPolicy.technicalPosition(39));
    }
}
