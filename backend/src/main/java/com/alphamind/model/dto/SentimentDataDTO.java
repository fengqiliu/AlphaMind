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
    /** MISSING / NON_POSITIVE / POSITIVE；缺失 PE 不等同于亏损。 */
    private String valuationStatus;
    /** 实际参与计算的信号及权重，旧报告没有此字段。 */
    private String scoreExplanation;
    private List<String> dataGaps;
    private String analysisSummary;
    // LLM生成的舆情综合摘要
    private String aiSummary;

    /** 最新新闻标题（来自真实新闻源，按发布时间倒序）。新闻源不可用时为 null。 */
    private List<String> recentHeadlines;

    /** 新闻标题关键词情绪信号（0.2~0.8，0.5 中性）。新闻源不可用时为 null。 */
    private Double newsSignal;

    /** 新闻来源（如 eastmoney）。新闻源不可用时为 null。 */
    private String newsDataSource;

    /** true 表示新闻来自过期缓存，而非实时新闻源。 */
    private Boolean newsStale;
}
