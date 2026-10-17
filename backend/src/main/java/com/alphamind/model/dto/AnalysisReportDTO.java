package com.alphamind.model.dto;

import com.alphamind.model.enums.SignalType;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportDTO {
    private String id;
    private String stockCode;
    private String stockName;
    /** 旧报告未记录的执行配置保持 null。 */
    private StrategyType strategy;
    private AnalysisMode mode;
    private SignalType finalSignal;
    private ConfidenceDTO confidence;
    private TradeSignalDTO tradeSignal;
    private MarketDataDTO marketData;
    private TechnicalIndicatorsDTO technicalIndicators;
    private SentimentDataDTO sentimentData;
    private JudgmentDTO judgment;
    private com.alphamind.investment.PersonalModelAssessment personalModel;
    /** 辩论模式下三方 (Bull/Bear/Neutral) 各自的观点，pipeline 模式为 null */
    private List<DebateViewDTO> debateViews;
    private LocalDateTime createdAt;
}
