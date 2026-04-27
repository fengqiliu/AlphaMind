package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 舆情Agent - 负责分析市场舆情和情绪
 */
@Slf4j
@Component
public class SentimentAgent extends BaseAgent {

    public SentimentAgent() {
        super(AgentType.SENTIMENT);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始舆情分析: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            if (marketData == null) {
                throw new RuntimeException("缺少市场数据");
            }

            // 分析舆情数据
            SentimentDataDTO sentimentData = analyzeSentiment(report.getStockCode(), marketData);

            report.setSentimentData(sentimentData);
            setContext("sentimentData", sentimentData);

            logInfo("舆情分析完成，情感评分: " + sentimentData.getSentimentScore());
            return report;
        } catch (Exception e) {
            logError("舆情分析失败", e);
            throw new RuntimeException("舆情分析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        SentimentDataDTO sentimentData = getContext("sentimentData");

        StringBuilder response = new StringBuilder();
        response.append("**舆情分析报告**\n\n");

        response.append("**舆情评分**: ").append(String.format("%.1f", sentimentData.getSentimentScore() * 100)).append("/100\n");
        response.append("**舆情趋势**: ").append(sentimentData.getSentimentTrend()).append("\n\n");

        if (!sentimentData.getPositiveFactors().isEmpty()) {
            response.append("**利好因素**:\n");
            sentimentData.getPositiveFactors().forEach(factor ->
                    response.append("- ").append(factor).append("\n"));
            response.append("\n");
        }

        if (!sentimentData.getNegativeFactors().isEmpty()) {
            response.append("**利空因素**:\n");
            sentimentData.getNegativeFactors().forEach(factor ->
                    response.append("- ").append(factor).append("\n"));
            response.append("\n");
        }

        response.append("**媒体关注度**: ").append(String.format("%.1f", sentimentData.getMediaAttention() * 100)).append("/100\n\n");
        response.append("**总结**: ").append(sentimentData.getAnalysisSummary());

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response.toString())
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(getModelName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名专业的市场舆情分析师，负责分析股票相关的新闻、研报、社交媒体等舆情信息。

            你的职责：
            1. 收集和分析股票相关的新闻报道
            2. 分析券商研报的投资评级和目标价
            3. 监测社交媒体和论坛的市场情绪
            4. 识别潜在的利好和利空因素
            5. 评估媒体关注度和市场热度

            舆情评分标准：
            - 70-100分: 舆情偏正面
            - 40-70分: 舆情中性
            - 0-40分: 舆情偏负面

            回答要求：
            - 客观呈现利好和利空因素
            - 注明信息来源的可信度
            - 给出舆情总结
            """;
    }

    /**
     * 分析舆情 (模拟实现)
     * TODO: 接入真实新闻数据源
     */
    private SentimentDataDTO analyzeSentiment(String stockCode, MarketDataDTO marketData) {
        return SentimentDataDTO.builder()
                .sentimentScore(0.65)
                .sentimentTrend("稳中向好")
                .positiveFactors(List.of(
                        "公司发布一季度业绩预告，净利润同比增长15%",
                        "多家券商维持"强烈推荐"评级",
                        "机构投资者持续增持",
                        "行业政策利好出台"
                ))
                .negativeFactors(List.of(
                        "原材料成本上升压力",
                        "行业竞争加剧"
                ))
                .newsCountBySource(Map.of(
                        "东方财富", 25,
                        "同花顺", 18,
                        "雪球", 42,
                        "微博", 156
                ))
                .mediaAttention(0.72)
                .analysisSummary("该股票近期舆情整体偏正面，机构评级积极，但需关注成本端压力。建议持续跟踪后续舆情变化。")
                .build();
    }
}
