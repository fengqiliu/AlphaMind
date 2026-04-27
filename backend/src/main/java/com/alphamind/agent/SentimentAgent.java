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

            // 调用LLM生成舆情综合摘要
            String aiSummary = generateSentimentSummary(sentimentData, marketData);
            sentimentData.setAiSummary(aiSummary);

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
        if (sentimentData == null) {
            return buildMsg("暂无舆情数据，请先发起分析。");
        }

        // 优先使用LLM生成回复
        String response = llmCall(getSystemPrompt(), buildSentimentPrompt(sentimentData, userMessage.getContent()));
        if (response == null) {
            response = buildSentimentTemplateResponse(sentimentData);
        }
        return buildMsg(response);
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
            - 给出舆情总结和趋势判断
            """;
    }

    private String generateSentimentSummary(SentimentDataDTO data, MarketDataDTO market) {
        String prompt = String.format(
                "请简要分析 %s 的市场舆情：舆情评分%.0f/100，趋势:%s，利好%d项，利空%d项，媒体关注度%.0f%%",
                market.getStockName(), data.getSentimentScore() * 100, data.getSentimentTrend(),
                data.getPositiveFactors().size(), data.getNegativeFactors().size(),
                data.getMediaAttention() * 100);
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : data.getAnalysisSummary();
    }

    private String buildSentimentPrompt(SentimentDataDTO data, String question) {
        return String.format("""
                舆情数据：
                - 舆情评分: %.0f/100
                - 舆情趋势: %s
                - 利好因素: %s
                - 利空因素: %s
                - 媒体关注度: %.0f/100

                用户问题：%s
                """,
                data.getSentimentScore() * 100, data.getSentimentTrend(),
                String.join("；", data.getPositiveFactors()),
                String.join("；", data.getNegativeFactors()),
                data.getMediaAttention() * 100, question);
    }

    private String buildSentimentTemplateResponse(SentimentDataDTO data) {
        StringBuilder sb = new StringBuilder("**舆情分析报告**\n\n");
        sb.append(String.format("**舆情评分**: %.0f/100\n", data.getSentimentScore() * 100));
        sb.append("**舆情趋势**: ").append(data.getSentimentTrend()).append("\n\n");
        if (!data.getPositiveFactors().isEmpty()) {
            sb.append("**利好因素**:\n");
            data.getPositiveFactors().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
        if (!data.getNegativeFactors().isEmpty()) {
            sb.append("**利空因素**:\n");
            data.getNegativeFactors().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
        sb.append(String.format("**媒体关注度**: %.0f/100\n\n", data.getMediaAttention() * 100));
        sb.append("**总结**: ").append(data.getAnalysisSummary());
        return sb.toString();
    }

    private ChatMessage buildMsg(String content) {
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * 分析舆情（模拟实现 - TODO: 接入真实新闻数据源）
     */
    private SentimentDataDTO analyzeSentiment(String stockCode, MarketDataDTO marketData) {
        // 根据涨跌幅动态调整舆情评分
        double changePct = marketData.getChangePercent() != null ? marketData.getChangePercent() : 0;
        double baseScore = 0.55 + changePct * 0.02;
        baseScore = Math.max(0.2, Math.min(0.9, baseScore));

        String trend = baseScore > 0.6 ? "稳中向好" : baseScore > 0.4 ? "震荡观望" : "谨慎悲观";

        return SentimentDataDTO.builder()
                .sentimentScore(Math.round(baseScore * 100) / 100.0)
                .sentimentTrend(trend)
                .positiveFactors(List.of(
                        "公司近期业绩预告超市场预期",
                        "多家券商维持\"推荐\"评级",
                        "行业政策边际改善",
                        "机构投资者持续关注"
                ))
                .negativeFactors(List.of(
                        "宏观经济不确定性仍存在",
                        "行业竞争加剧，毛利率承压"
                ))
                .newsCountBySource(Map.of(
                        "东方财富", 18,
                        "同花顺", 12,
                        "雪球", 35,
                        "微博", 88
                ))
                .mediaAttention(Math.min(0.95, 0.5 + Math.abs(changePct) * 0.05 + Math.random() * 0.2))
                .analysisSummary(String.format("该股近期舆情%s，利好因素较多，但需关注宏观风险。",
                        baseScore > 0.6 ? "整体偏正面" : "处于中性偏谨慎状态"))
                .build();
    }
}
