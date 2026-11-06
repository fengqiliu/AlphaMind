package com.alphamind.portfolio;

import jakarta.validation.constraints.*;

/**
 * 一条持仓。有 6 位代码和股数时按实时价估值；否则（如场外基金）必须给出手工市值。
 * 模型内标的的主题以模型为准，保存时覆盖。
 */
public record Holding(
        @Pattern(regexp = "\\d{6}") String stockCode,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 30) String theme,
        @DecimalMin("0") Double shares,
        @DecimalMin("0") Double manualValue) {

    public boolean pricedByMarket() {
        return stockCode != null && shares != null;
    }
}
