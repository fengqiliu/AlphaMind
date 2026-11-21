package com.alphamind.portfolio;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** 一个当前处于越线状态的体检项；key 由种类与标的代码（主题/组合项用名称）组成。 */
@Entity
@Table(name = "portfolio_health_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PortfolioHealthStateEntity {
    @Id
    @Column(name = "finding_key", length = 120)
    private String findingKey;
    @Column(name = "kind", length = 30, nullable = false)
    private String kind;
    @Column(name = "active_since", nullable = false)
    private OffsetDateTime activeSince;
}
