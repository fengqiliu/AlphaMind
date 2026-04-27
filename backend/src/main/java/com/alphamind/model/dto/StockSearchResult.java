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
    private Double currentPrice;
    private Double changePercent;
}
