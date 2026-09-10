package com.alphamind.service;

import com.alphamind.model.dto.*;
import com.alphamind.model.entity.AnalysisReportEntity;
import com.alphamind.model.enums.ConfidenceLevel;
import com.alphamind.model.enums.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * AnalysisReportDTO 与 PostgreSQL 实体之间的统一映射器。
 * Controller、聊天上下文恢复等调用方共用同一套还原规则，避免字段漂移。
 */
@Component
@RequiredArgsConstructor
public class AnalysisReportMapper {

    private final ObjectMapper objectMapper;

    public AnalysisReportEntity toEntity(AnalysisReportDTO dto) {
        AnalysisReportEntity.AnalysisReportEntityBuilder builder = AnalysisReportEntity.builder()
                .id(dto.getId())
                .stockCode(dto.getStockCode())
                .stockName(dto.getStockName() != null ? dto.getStockName() : dto.getStockCode())
                .marketData(toJsonValue(dto.getMarketData()))
                .technicalIndicators(toJsonValue(dto.getTechnicalIndicators()))
                .sentimentData(toJsonValue(dto.getSentimentData()))
                .judgment(toJsonValue(dto.getJudgment()));

        if (dto.getFinalSignal() != null) {
            builder.signalType(dto.getFinalSignal().name());
        }
        if (dto.getConfidence() != null) {
            if (dto.getConfidence().getValue() != null) {
                builder.confidenceValue(BigDecimal.valueOf(dto.getConfidence().getValue()));
            }
            if (dto.getConfidence().getLevel() != null) {
                builder.confidenceLevel(dto.getConfidence().getLevel().name());
            }
        }
        if (dto.getTradeSignal() != null) {
            TradeSignalDTO signal = dto.getTradeSignal();
            if (signal.getType() != null) builder.signalType(signal.getType().name());
            if (signal.getEntryPrice() != null) builder.entryPrice(BigDecimal.valueOf(signal.getEntryPrice()));
            if (signal.getTargetPrice() != null) builder.targetPrice(BigDecimal.valueOf(signal.getTargetPrice()));
            if (signal.getStopLoss() != null) builder.stopLoss(BigDecimal.valueOf(signal.getStopLoss()));
            builder.holdingDays(signal.getHoldingPeriodDays());
            builder.rationale(signal.getRationale());
        }
        if (dto.getCreatedAt() != null) {
            builder.createdAt(dto.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        }
        return builder.build();
    }

    /** Hibernate 的 JSONB 类型需要可序列化的 JSON 值，不能直接绑定 DTO 实例。 */
    private Object toJsonValue(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("分析报告 JSON 字段序列化失败", exception);
        }
    }

    public AnalysisReportDTO toDTO(AnalysisReportEntity entity) {
        AnalysisReportDTO dto = new AnalysisReportDTO();
        dto.setId(entity.getId());
        dto.setStockCode(entity.getStockCode());
        dto.setStockName(entity.getStockName());

        if (entity.getMarketData() != null) {
            dto.setMarketData(fromJsonValue(entity.getMarketData(), MarketDataDTO.class));
        }
        if (entity.getTechnicalIndicators() != null) {
            dto.setTechnicalIndicators(fromJsonValue(
                    entity.getTechnicalIndicators(), TechnicalIndicatorsDTO.class));
        }
        if (entity.getSentimentData() != null) {
            dto.setSentimentData(fromJsonValue(entity.getSentimentData(), SentimentDataDTO.class));
        }
        if (entity.getJudgment() != null) {
            dto.setJudgment(fromJsonValue(entity.getJudgment(), JudgmentDTO.class));
        }

        if (entity.getEntryPrice() != null || entity.getTargetPrice() != null) {
            TradeSignalDTO signal = new TradeSignalDTO();
            signal.setType(parseSignalType(entity.getSignalType()));
            if (entity.getEntryPrice() != null) signal.setEntryPrice(entity.getEntryPrice().doubleValue());
            if (entity.getTargetPrice() != null) signal.setTargetPrice(entity.getTargetPrice().doubleValue());
            if (entity.getStopLoss() != null) signal.setStopLoss(entity.getStopLoss().doubleValue());
            if (entity.getHoldingDays() != null) signal.setHoldingPeriodDays(entity.getHoldingDays());
            signal.setRationale(entity.getRationale());
            dto.setTradeSignal(signal);
        }

        dto.setFinalSignal(parseSignalType(entity.getSignalType()));

        if (entity.getConfidenceValue() != null || entity.getConfidenceLevel() != null) {
            ConfidenceDTO confidence = new ConfidenceDTO();
            if (entity.getConfidenceValue() != null) {
                confidence.setValue(entity.getConfidenceValue().doubleValue());
            }
            confidence.setLevel(parseConfidenceLevel(entity.getConfidenceLevel()));
            dto.setConfidence(confidence);
        }

        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().toLocalDateTime());
        }
        return dto;
    }

    private <T> T fromJsonValue(Object value, Class<T> targetType) {
        try {
            if (value instanceof String json) {
                return objectMapper.readValue(json, targetType);
            }
            return objectMapper.convertValue(value, targetType);
        } catch (Exception exception) {
            throw new IllegalArgumentException("分析报告 JSON 字段反序列化失败", exception);
        }
    }

    private SignalType parseSignalType(String value) {
        if (value == null) return null;
        try {
            return SignalType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ConfidenceLevel parseConfidenceLevel(String value) {
        if (value == null) return null;
        try {
            return ConfidenceLevel.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
