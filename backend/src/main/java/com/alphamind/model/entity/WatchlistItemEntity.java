package com.alphamind.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 用户自选股实体
 */
@Entity
@Table(
    name = "watchlist_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stock_code"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 100, nullable = false)
    @Builder.Default
    private String userId = "default";

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Column(name = "stock_name", length = 100, nullable = false)
    private String stockName;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
