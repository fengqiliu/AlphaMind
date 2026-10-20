package com.alphamind.service;

import com.alphamind.model.dto.*;
import com.alphamind.model.entity.AnalysisReportEntity;
import com.alphamind.model.enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisReportMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AnalysisReportMapper mapper = new AnalysisReportMapper(objectMapper);

    @Test
    void restoresCompleteDebateReportIncludingDifferentFinalAndTradeSignals() {
        ConfidenceDTO confidence = ConfidenceDTO.builder().value(0.82).level(ConfidenceLevel.HIGH)
                .explanation("三方观点与风险说明").build();
        AnalysisReportDTO original = AnalysisReportDTO.builder().id("roundtrip").stockCode("600519").stockName("贵州茅台")
                .strategy(StrategyType.CONSERVATIVE).mode(AnalysisMode.DEBATE).finalSignal(SignalType.HOLD)
                .confidence(confidence)
                .tradeSignal(TradeSignalDTO.builder().type(SignalType.BUY).entryPrice(100.0).targetPrice(110.0)
                        .stopLoss(95.0).holdingPeriodDays(45).rationale("原始建议").build())
                .marketData(MarketDataDTO.builder().stockCode("600519").currentPrice(100.0).build())
                .technicalIndicators(TechnicalIndicatorsDTO.builder().technicalScore(80).build())
                .sentimentData(SentimentDataDTO.builder().sentimentScore(0.7).recentHeadlines(List.of("真实标题"))
                        .valuationStatus("MISSING").scoreExplanation("可用信号归一化")
                        .dataGaps(List.of("PE缺失，不代表亏损")).mediaAttention(null).build())
                .judgment(JudgmentDTO.builder().finalPosition(DebatePosition.NEUTRAL).confidence(confidence)
                        .reasoning("最终判断").build())
                .debateViews(List.of(view(AgentType.BULL, DebatePosition.BULLISH), view(AgentType.BEAR, DebatePosition.BEARISH),
                        view(AgentType.NEUTRAL, DebatePosition.NEUTRAL)))
                .createdAt(LocalDateTime.of(2026, 10, 16, 12, 30)).build();

        AnalysisReportEntity entity = mapper.toEntity(original);
        assertEquals("CONSERVATIVE", entity.getStrategy());
        assertTrue(entity.getEnableDebate());
        // PostgreSQL/JDBC 可用 UTC 返回带时区时间；读取后应恢复原始本地时间。
        entity.setCreatedAt(entity.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC));
        assertEquals(original, mapper.toDTO(entity));
    }

    @Test
    void pipelineConfigurationDoesNotBecomeDefaultDebateAfterReload() {
        AnalysisReportDTO report = AnalysisReportDTO.builder().id("pipeline").stockCode("000858")
                .strategy(StrategyType.AGGRESSIVE).mode(AnalysisMode.PIPELINE).build();
        AnalysisReportEntity entity = mapper.toEntity(report);
        assertFalse(entity.getEnableDebate());
        assertEquals(AnalysisMode.PIPELINE, mapper.toDTO(entity).getMode());
        assertEquals(StrategyType.AGGRESSIVE, mapper.toDTO(entity).getStrategy());
        assertNull(mapper.toDTO(entity).getDebateViews());
    }

    @Test
    void legacyDefaultsAreNotPresentedAsKnownExecutionSettings() {
        AnalysisReportEntity legacy = AnalysisReportEntity.builder().id("legacy").stockCode("600519")
                .signalType("HOLD").entryPrice(BigDecimal.TEN).build();
        AnalysisReportDTO report = mapper.toDTO(legacy);
        assertNull(report.getStrategy());
        assertNull(report.getMode());
        assertNull(report.getDebateViews());
        assertEquals(SignalType.HOLD, report.getFinalSignal());
        assertEquals(10.0, report.getTradeSignal().getEntryPrice());
    }

    @Test
    void restoresJsonObjectsAndArraysAsWellAsLegacyJsonStrings() {
        AnalysisReportEntity entity = AnalysisReportEntity.builder().id("objects").stockCode("600519")
                .marketData(java.util.Map.of("currentPrice", 123.0))
                .debateViews(List.of(java.util.Map.of("agentType", "BULL", "position", "BULLISH", "view", "观点")))
                .build();
        AnalysisReportDTO report = mapper.toDTO(entity);
        assertEquals(123.0, report.getMarketData().getCurrentPrice());
        assertEquals(AgentType.BULL, report.getDebateViews().get(0).getAgentType());
    }

    private static DebateViewDTO view(AgentType agent, DebatePosition position) {
        return DebateViewDTO.builder().agentType(agent).position(position).view(agent.name() + "观点")
                .reasons(List.of("理由")).keyPoints(List.of("观察点")).attackPoints(List.of("反驳点")).build();
    }
}
