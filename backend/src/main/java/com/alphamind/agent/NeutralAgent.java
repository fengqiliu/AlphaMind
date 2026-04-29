package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.ConfidenceLevel;
import com.alphamind.model.enums.DebatePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

        String viewText;
        String llmPrompt = String.format("""
                股票: %s, 当前价: ¥%.2f
                技术评分: %d/100, 舆情评分: %.0f/100
                请从客观中立立场，列出该股相等分量的多空双方因素，并给出情景分析。
                """,
                market != null ? market.getStockName() : "N/A",
                market != null ? market.getCurrentPrice() : 0,
                tech != null ? tech.getTechnicalScore() : 0,
                sentiment != null ? sentiment.getSentimentScore() * 100 : 0);
        String llmResult = llmCall(getSystemPrompt(), llmPrompt);
        viewText = llmResult != null ? llmResult : generateNeutralAnalysis(market, tech, sentiment);

        List<String> reasons = extractNeutralReasons(market, tech, sentiment);
        double confValue = 0.60; // 中立立场置信度中性

        DebateViewDTO neutralView = DebateViewDTO.builder()
                .position(DebatePosition.NEUTRAL)
                .agentType(AgentType.NEUTRAL)
                .view(viewText)
                .reasons(reasons)
                .keyPoints(reasons.stream().limit(3).toList())
                .targetPrice(null) // 中立不给出目标价
                .upsidePotential(null)
                .confidence(ConfidenceDTO.builder()
                        .value(confValue)
                        .level(ConfidenceLevel.MEDIUM)
                        .explanation("中立客观，不偏任何一方")
                        .build())
                .build();

        setContext("neutralView", neutralView);
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

    private List<String> extractNeutralReasons(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment) {
        List<String> reasons = new ArrayList<>();
        if (tech != null) {
            int score = tech.getTechnicalScore();
            if (score >= 60) reasons.add("技术面偶尔偏多，评分 " + score + "/100");
            else if (score <= 40) reasons.add("技术面偶尔偏空，评分 " + score + "/100");
            else reasons.add("技术面方向未明，评分 " + score + "/100");
        }
        if (market != null && market.getPe() != null) {
            if (market.getPe() > 0 && market.getPe() < 20) reasons.add("低估值对多头有利，但需观察基本面贔写风险");
            else if (market.getPe() > 50) reasons.add("高估值对空头有利，但需评估成长性支撑");
            else reasons.add("PE " + String.format("%.1f", market.getPe()) + "，处于合理区间");
        }
        if (sentiment != null) {
            double score = sentiment.getSentimentScore();
            int posCount = sentiment.getPositiveFactors() != null ? sentiment.getPositiveFactors().size() : 0;
            int negCount = sentiment.getNegativeFactors() != null ? sentiment.getNegativeFactors().size() : 0;
            reasons.add(String.format("舆情%d正面/%d负面，得分%.0f/100", posCount, negCount, score * 100));
        }
        if (reasons.isEmpty()) reasons.add("当前多空互博，建议观望为主，待信号明确后操作");
        return reasons;
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
