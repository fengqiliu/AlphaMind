package com.alphamind.portfolio;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 某个交易日收盘后的组合总资产。cashRatio 为现金与低波动资产占总资产的百分比。 */
@Entity
@Table(name = "portfolio_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshotEntity {
    @Id
    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;
    @Column(name = "total_value", nullable = false)
    private double totalValue;
    @Column(name = "cash_ratio", nullable = false)
    private double cashRatio;
    @Column(name = "holdings_count", nullable = false)
    private int holdingsCount;
    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}
