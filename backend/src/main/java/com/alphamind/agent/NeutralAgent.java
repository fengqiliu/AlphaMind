package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.DebatePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 中立Agent - 保持中立客观分析
 */
@Slf4j
@Component
public class NeutralAgent extends BaseAgent {

    public NeutralAgent() {
        super(AgentType.NEUTRAL);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始中立分析: " + report.getStockCode());
        MarketDataDTO market = getContext("marketData");
        TechnicalIndicatorsDTO tech = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String argument = generateNeutralAnalysis(market, tech, sentiment);
        setContext("neutralArgument", argument);
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String prompt = String.format("""
                股票: %s, 当前价: ¥%.2f
                技术评分: %d/100, 舆情评分: %.0f/100
                用户问题: %s
                请从中立客观角度分析多空双方观点，给出情景分析。
                """,
                marketData != null ? marketData.getStockName() : "N/A",
                marketData != null ? marketData.getCurrentPrice() : 0,
                technical != null ? technical.getTechnicalScore() : 0,
                sentiment != null ? sentiment.getSentimentScore() * 100 : 0,
                userMessage.getContent());

        String response = llmCall(getSystemPrompt(), prompt);
        if (response == null) {
            response = generateNeutralAnalysis(marketData, technical, sentiment);
        }

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名客观中立的市场分析师，负责平衡评估多空双方观点。

            你的立场：
            - 你是中立阵营的辩手
            - 你需要公平呈现多空双方的观点
            - 你相信客观分析比预设立场更重要

            分析框架：
            1. 平衡列出技术面的多空信号
            2. 客观呈现基本面的利好和利空
            3. 分析舆情中的正面和负面因素
            4. 识别当前市场的主要矛盾
            5. 给出中性的情景分析

            输出要求：
            - 公平呈现多空双方观点
            - 不偏袒任何一方
            - 给出中性的投资建议
            - 明确指出关键的不确定性因素
            """;
    }

    public DebatePosition getPosition() {
        return DebatePosition.NEUTRAL;
    }

    public int getVoteWeight() {
        return 1;
    }

    private String generateNeutralAnalysis(MarketDataDTO marketData, TechnicalIndicatorsDTO technical, SentimentDataDTO sentiment) {
        if (marketData == null) return "【中立观点】数据暂不可用，请先发起分析。";

        StringBuilder analysis = new StringBuilder();
        analysis.append("**【中立观点】多空博弈，短期维持震荡**\n\n");
        analysis.append("**多空平衡分析**:\n\n");

        if (technical != null) {
            analysis.append("1️⃣ **技术面**\n");
            int techScore = technical.getTechnicalScore();
            if (techScore >= 60) {
                analysis.append("   多头: 技术评分").append(techScore).append("，趋势向好\n");
            } else if (techScore <= 40) {
                analysis.append("   空头: 技术评分").append(techScore).append("，趋势偏弱\n");
            } else {
                analysis.append("   中性: 技术评分").append(techScore).append("，方向不明\n");
            }
            if (technical.getBollinger() != null) {
                analysis.append(String.format("   当前价格 ¥%.2f，接近布林带¥%.2f中轨\n\n", marketData.getCurrentPrice(), technical.getBollinger().getMiddle()));
            }
        }

        analysis.append("2️⃣ **基本面**\n");
        if (marketData.getPe() != null) {
            if (marketData.getPe() > 35) {
                analysis.append("   空头: PE ").append(String.format("%.1f", marketData.getPe())).append(" 偏高\n");
            } else if (marketData.getPe() < 20) {
                analysis.append("   多头: PE ").append(String.format("%.1f", marketData.getPe())).append(" 偏低\n");
            } else {
                analysis.append("   中性: PE ").append(String.format("%.1f", marketData.getPe())).append(" 合理\n");
            }
        }
        if (marketData.getMarketCap() != null) {
            analysis.append(String.format("   市值 %.2f亿，流动性良好\n\n", marketData.getMarketCap() / 1e8));
        }

        if (sentiment != null) {
            analysis.append("3️⃣ **舆情面**\n");
            double sentScore = sentiment.getSentimentScore();
            analysis.append(String.format("   舆情评分 %.0f/100，", sentScore * 100));
            if (sentScore > 0.6) analysis.append("情绪偏暖\n");
            else if (sentScore < 0.4) analysis.append("情绪偏冷\n");
            else analysis.append("情绪中性\n");
            int posCount = sentiment.getPositiveFactors() != null ? sentiment.getPositiveFactors().size() : 0;
            int negCount = sentiment.getNegativeFactors() != null ? sentiment.getNegativeFactors().size() : 0;
            analysis.append(String.format("   利好因素 %d项，利空因素 %d项\n\n", posCount, negCount));
        }

        if (technical != null && technical.getBollinger() != null) {
            analysis.append("**情景分析**:\n");
            analysis.append("   📈 上涨情景: 若突破¥").append(String.format("%.2f", technical.getBollinger().getUpper()))
                    .append("，目标看¥").append(String.format("%.2f", marketData.getCurrentPrice() * 1.1)).append("\n");
            analysis.append("   📉 下跌情景: 若跌破¥").append(String.format("%.2f", technical.getBollinger().getLower()))
                    .append("，止损看¥").append(String.format("%.2f", marketData.getCurrentPrice() * 0.95)).append("\n");
            analysis.append("   ⚖️ 横盘情景: 在布林带区间震荡，观望为宜\n");
        }

        analysis.append("\n**建议**: 保持中性仓位，等待方向明确后再加仓或减仓");
        return analysis.toString();
    }
}
