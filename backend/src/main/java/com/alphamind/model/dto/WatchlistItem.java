package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItem {
    private String stockCode;
    private String stockName;
    private LocalDateTime addedAt;

    /** 实时价格。行情源不可用时为 null——不要用 0 冒充价格。 */
    private Double currentPrice;

    /** 相对昨收的涨跌额。无价格时为 null。 */
    private Double change;

    /** 相对昨收的涨跌幅（%）。无价格时为 null。 */
    private Double changePercent;

    /** 价格来源（如 sina）。无价格时为 null。 */
    private String dataSource;

    /** true 表示价格来自过期缓存，而非实时行情。 */
    private Boolean stale;
}
