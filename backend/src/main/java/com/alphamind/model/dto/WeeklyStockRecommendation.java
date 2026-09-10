package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyStockRecommendation {
    private Integer rank;
    private String weekLabel;
    private String stockCode;
    private String stockName;
    private String industry;
    private String market;
    private Double currentPrice;
    private Double changePercent;
    private Double lowPositionScore;
    private Double valueScore;
    private Double compositeScore;
    private String summary;
    private List<String> highlights;

    /** 打分所依据的价格来源（如 sina）。 */
    private String dataSource;

    /** true 表示评分依据的是过期缓存价格，需在前端标注。 */
    private Boolean stale;
}