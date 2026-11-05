package com.alphamind.investment.alert;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** 每个标的最近一次检查到的档位，以及自上次回到首档之上以来已提醒过的最深档位。 */
@Entity
@Table(name = "price_alert_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertStateEntity {
    @Id
    @Column(name = "stock_code", length = 20)
    private String stockCode;
    @Column(name = "last_zone", length = 20, nullable = false)
    private String lastZone;
    @Column(name = "last_price", nullable = false)
    private double lastPrice;
    @Column(name = "deepest_alerted", nullable = false)
    private int deepestAlerted;
    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;
}
