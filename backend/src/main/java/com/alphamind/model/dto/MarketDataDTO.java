package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataDTO {
    private String stockCode;
    private String stockName;
    private Double currentPrice;
    private Double change;
    private Double changePercent;
    private Double open;
    private Double high;
    private Double low;
    private Long volume;
    private Double amount;
    private Double turnoverRate;
    private Double pe;
    private Double pb;
    private Long marketCap;
    private String updateTime;
    // LLM生成的行情摘要
    private String aiSummary;
}
