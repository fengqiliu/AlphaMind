package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentDataDTO {
    private Double sentimentScore;
    private String sentimentTrend;
    private List<String> positiveFactors;
    private List<String> negativeFactors;
    private Map<String, Integer> newsCountBySource;
    private Double mediaAttention;
    private String analysisSummary;
}
