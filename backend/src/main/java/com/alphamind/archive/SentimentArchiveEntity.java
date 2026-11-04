package com.alphamind.archive;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** 舆情输入存档行：记录当时的行情代理输入、新闻信号与最终分数，只增不改。 */
@Entity
@Table(name = "sentiment_archive")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SentimentArchiveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", length = 20, nullable = false, updatable = false)
    private String stockCode;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private OffsetDateTime computedAt;

    @Column(name = "method", length = 40, nullable = false, updatable = false)
    private String method;

    @Column(name = "sentiment_score", updatable = false)
    private Double sentimentScore;

    @Column(name = "signal_coverage", updatable = false)
    private Double signalCoverage;

    @Column(name = "news_signal", updatable = false)
    private Double newsSignal;

    @Column(name = "news_data_source", length = 40, updatable = false)
    private String newsDataSource;

    @Column(name = "news_stale", updatable = false)
    private Boolean newsStale;

    @Column(name = "headline_count", nullable = false, updatable = false)
    private int headlineCount;

    @Column(name = "change_percent", updatable = false)
    private Double changePercent;

    @Column(name = "pe", updatable = false)
    private Double pe;

    @Column(name = "turnover_rate", updatable = false)
    private Double turnoverRate;

    @Column(name = "valuation_status", length = 20, updatable = false)
    private String valuationStatus;

    @Column(name = "market_update_time", length = 40, updatable = false)
    private String marketUpdateTime;

    @Column(name = "score_explanation", columnDefinition = "TEXT", updatable = false)
    private String scoreExplanation;
}
