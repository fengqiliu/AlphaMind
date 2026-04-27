package com.alphamind.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 分析报告实体 - 持久化到 PostgreSQL
 * 复杂对象（行情/技术指标/舆情/仲裁结果）存储为 jsonb，保持灵活性
 */
@Entity
@Table(name = "analysis_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Column(name = "stock_name", length = 100, nullable = false)
    private String stockName;

    @Column(name = "strategy", length = 20, nullable = false)
    @Builder.Default
    private String strategy = "BALANCED";

    @Column(name = "enable_debate", nullable = false)
    @Builder.Default
    private Boolean enableDebate = true;

    // 交易信号
    @Column(name = "signal_type", length = 10)
    private String signalType;

    @Column(name = "entry_price", precision = 12, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "target_price", precision = 12, scale = 4)
    private BigDecimal targetPrice;

    @Column(name = "stop_loss", precision = 12, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "holding_days")
    private Integer holdingDays;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    // 置信度
    @Column(name = "confidence_value", precision = 5, scale = 4)
    private BigDecimal confidenceValue;

    @Column(name = "confidence_level", length = 10)
    private String confidenceLevel;

    // jsonb 字段 - 完整分析数据（Hibernate 6 原生 JSON 类型）
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "market_data", columnDefinition = "jsonb")
    private Object marketData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_indicators", columnDefinition = "jsonb")
    private Object technicalIndicators;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sentiment_data", columnDefinition = "jsonb")
    private Object sentimentData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judgment", columnDefinition = "jsonb")
    private Object judgment;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
