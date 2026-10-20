package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import com.alphamind.strategy.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoringIntegrationTest {
    @Test void riskStrategyDoesNotInflateEvidenceAgreement() {
        var agent = new PortfolioAgent(new StrategyRegistry(new ConservativeStrategyProfile(), new BalancedStrategyProfile(), new AggressiveStrategyProfile()), new StrategySignalPlanner());
        try {
            agent.setContext("marketData", MarketDataDTO.builder().currentPrice(100.0).build());
            agent.setContext("technicalIndicators", TechnicalIndicatorsDTO.builder().technicalScore(90).sampleSize(120).build());
            agent.setContext("sentimentData", SentimentDataDTO.builder().sentimentScore(0.9).signalCoverage(1.0).build());
            ConfidenceDTO previous = null;
            for (var strategy : StrategyType.values()) {
                agent.setContext("strategy", strategy);
                var confidence = agent.analyze(AnalysisReportDTO.builder().build()).getConfidence();
                if (previous != null) assertEquals(previous, confidence);
                previous = confidence;
            }
        } finally { agent.clearContext(); }
    }

    @Test void actualVoteBreakdownExplainsFinalPositionInBothDirections() {
        var agent = new ArbitratorAgent(new BullAgent(), new BearAgent(), new NeutralAgent());
        for (var data : new double[][]{{80,0.8},{20,0.2},{80,0.2},{50,0.5}}) {
            var votes = agent.calculateVoteBreakdown(TechnicalIndicatorsDTO.builder().technicalScore((int)data[0]).build(),
                    SentimentDataDTO.builder().sentimentScore(data[1]).build());
            assertEquals(6, votes.values().stream().mapToInt(Integer::intValue).sum());
            var expected = data[0] == 80 && data[1] == 0.8 ? DebatePosition.BULLISH
                    : data[0] == 20 ? DebatePosition.BEARISH : DebatePosition.NEUTRAL;
            assertEquals(expected, agent.calculateFinalPosition(votes));
            if (expected != DebatePosition.NEUTRAL) assertTrue(votes.get(expected) > 3);
        }
    }

    @Test void debateTemplatesDoNotManufactureCrossesValuationOrIndependentTargets() {
        for (BaseAgent agent : new BaseAgent[]{new BullAgent(),new BearAgent(),new NeutralAgent()}) {
            try {
                agent.setContext("marketData", MarketDataDTO.builder().stockCode("TEST").currentPrice(100.0).pe(-5.0).build());
                var text = agent.chat(ChatMessage.builder().content("说明观点").build()).getContent();
                assertTrue(text.contains("PE为负"));
                assertFalse(text.contains("估值合理"));
                assertFalse(text.contains("目标价格**: ¥"));
                assertFalse(text.contains("积极买入"));
                assertTrue(text.contains("未在本轮核实"));
            } finally { agent.clearContext(); }
        }
    }
}
