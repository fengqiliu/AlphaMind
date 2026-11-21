package com.alphamind.portfolio;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** 一次体检提醒。除"已读时间"外写入后不再修改。 */
@Entity
@Table(name = "portfolio_health_alerts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PortfolioHealthAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "kind", length = 30, nullable = false, updatable = false)
    private String kind;
    @Column(name = "stock_code", length = 20, updatable = false)
    private String stockCode;
    @Column(name = "name", length = 50, nullable = false, updatable = false)
    private String name;
    @Column(name = "message", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String message;
    @Column(name = "value", updatable = false)
    private Double value;
    @Column(name = "limit_value", updatable = false)
    private Double limitValue;
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private OffsetDateTime triggeredAt;
    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    public void acknowledge(OffsetDateTime at) {
        if (acknowledgedAt == null) acknowledgedAt = at;
    }
}
