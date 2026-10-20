package com.alphamind.agent;

import com.alphamind.model.dto.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebateAttentionTest {
    @Test
    void arbitrationHandlesMissingAndLegacyEstimatedAttentionWithoutRiskClaims() {
        var agent = new ArbitratorAgent(new BullAgent(), new BearAgent(), new NeutralAgent());
        try {
            agent.setContext("technicalIndicators", TechnicalIndicatorsDTO.builder().technicalScore(50)
                    .rsi(TechnicalIndicatorsDTO.RsiDTO.builder().rsi12(50.0).build()).build());
            for (Double attention : Arrays.asList(null, 0.1, 0.9)) {
                agent.setContext("sentimentData", SentimentDataDTO.builder().sentimentScore(0.5)
                        .mediaAttention(attention).build());
                var report = agent.analyze(AnalysisReportDTO.builder().stockCode("600519").build());
                var warnings = report.getJudgment().getRiskWarnings();
                assertTrue(warnings.contains("媒体关注度缺少实测来源，相关风险未评估"));
                assertFalse(warnings.stream().anyMatch(value -> value.contains("泡沫") || value.contains("催化剂")));
            }
        } finally {
            agent.clearContext();
        }
    }

    @Test
    void bearTemplateDoesNotTreatLegacyEstimateAsObservedBubbleRisk() {
        var agent = new BearAgent();
        try {
            agent.setContext("marketData", MarketDataDTO.builder().currentPrice(100.0).build());
            agent.setContext("sentimentData", SentimentDataDTO.builder().sentimentScore(0.5)
                    .mediaAttention(0.9).negativeFactors(List.of()).build());
            var response = agent.chat(ChatMessage.builder().content("说明风险").build());
            assertTrue(response.getContent().contains("媒体关注度缺少实测来源"));
            assertFalse(response.getContent().contains("媒体关注度过高"));
        } finally {
            agent.clearContext();
        }
    }
}
