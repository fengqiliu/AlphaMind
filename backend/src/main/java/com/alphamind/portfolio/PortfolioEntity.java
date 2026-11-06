package com.alphamind.portfolio;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "portfolio_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEntity {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "holdings", columnDefinition = "jsonb", nullable = false)
    private List<Holding> holdings;
    @Column(name = "cash", nullable = false)
    private double cash;
    @Column(name = "low_volatility", nullable = false)
    private double lowVolatility;
    @Column(name = "peak_value")
    private Double peakValue;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    PortfolioProfile toProfile() {
        return new PortfolioProfile(List.copyOf(holdings), cash, lowVolatility, peakValue, updatedAt);
    }
}
