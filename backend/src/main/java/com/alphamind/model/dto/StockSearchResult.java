package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchResult {
    private String code;
    private String name;
    private String industry;
    private String market;

    /** 实时价格。行情源不可用时为 null——不要用 0 冒充价格。 */
    private Double currentPrice;

    /** 相对昨收的涨跌幅（%）。行情源不可用时为 null。 */
    private Double changePercent;

    /** 价格来源（如 sina）。无价格时为 null。 */
    private String dataSource;

    /** true 表示价格来自过期缓存，而非实时行情。 */
    private Boolean stale;
}
