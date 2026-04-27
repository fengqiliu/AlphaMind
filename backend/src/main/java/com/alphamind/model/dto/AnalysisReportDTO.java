package com.alphamind.model.dto;

import com.alphamind.model.enums.SignalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportDTO {
    private String id;
    private String stockCode;
    private String stockName;
    private SignalType finalSignal;
    private ConfidenceDTO confidence;
    private TradeSignalDTO tradeSignal;
    private MarketDataDTO marketData;
    private TechnicalIndicatorsDTO technicalIndicators;
    private SentimentDataDTO sentimentData;
    private JudgmentDTO judgment;
    private LocalDateTime createdAt;
}
