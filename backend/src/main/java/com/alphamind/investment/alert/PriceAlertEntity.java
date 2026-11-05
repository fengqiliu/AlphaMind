package com.alphamind.investment.alert;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** 一次档位提醒。除"已读时间"外写入后不再修改。 */
@Entity
@Table(name = "price_alerts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PriceAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stock_code", length = 20, nullable = false, updatable = false)
    private String stockCode;
    @Column(name = "stock_name", length = 50, nullable = false, updatable = false)
    private String stockName;
    @Column(name = "from_zone", length = 20, nullable = false, updatable = false)
    private String fromZone;
    @Column(name = "to_zone", length = 20, nullable = false, updatable = false)
    private String toZone;
    @Column(name = "price", nullable = false, updatable = false)
    private double price;
    @Column(name = "quoted_at", updatable = false)
    private OffsetDateTime quotedAt;
    @Column(name = "model_version", length = 20, nullable = false, updatable = false)
    private String modelVersion;
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private OffsetDateTime triggeredAt;
    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    public void acknowledge(OffsetDateTime at) {
        if (acknowledgedAt == null) acknowledgedAt = at;
    }
}
