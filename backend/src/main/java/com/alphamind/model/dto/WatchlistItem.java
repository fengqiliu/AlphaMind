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
    private Double currentPrice;
    private Double change;
    private Double changePercent;
}
