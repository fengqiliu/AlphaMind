package com.alphamind.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 一份报告在某个持有期的事后结果；写入后不再修改。 */
@Entity
@Table(name = "signal_outcomes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SignalOutcomeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", length = 36, nullable = false, updatable = false)
    private String reportId;
    @Column(name = "stock_code", length = 20, nullable = false, updatable = false)
    private String stockCode;
    @Column(name = "horizon_days", nullable = false, updatable = false)
    private int horizonDays;
    @Column(name = "signal_type", length = 10, updatable = false)
    private String signalType;
    @Column(name = "confidence_value", updatable = false)
    private Double confidenceValue;
    @Column(name = "strategy", length = 20, updatable = false)
    private String strategy;
    @Column(name = "analysis_mode", length = 20, updatable = false)
    private String analysisMode;
    @Column(name = "personal_level", length = 10, updatable = false)
    private String personalLevel;
    @Column(name = "signal_date", nullable = false, updatable = false)
    private LocalDate signalDate;
    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;
    @Column(name = "entry_price", nullable = false, updatable = false)
    private double entryPrice;
    @Column(name = "exit_date", nullable = false, updatable = false)
    private LocalDate exitDate;
    @Column(name = "exit_price", nullable = false, updatable = false)
    private double exitPrice;
    @Column(name = "gross_return", nullable = false, updatable = false)
    private double grossReturn;
    @Column(name = "net_return", nullable = false, updatable = false)
    private double netReturn;
    @Column(name = "benchmark_return", updatable = false)
    private Double benchmarkReturn;
    @Column(name = "excess_return", updatable = false)
    private Double excessReturn;
    @Column(name = "entry_blocked", nullable = false, updatable = false)
    private boolean entryBlocked;
    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private OffsetDateTime evaluatedAt;
}
