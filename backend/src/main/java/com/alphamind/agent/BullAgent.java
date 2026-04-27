package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.DebatePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多头Agent - 从看多角度分析股票
 */
@Slf4j
@Component
public class BullAgent extends BaseAgent {

    public BullAgent() {
        super(AgentType.BULL);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始多头分析: " + report.getStockCode());
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String analysis = generateBullishAnalysis(marketData, technical, sentiment);

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(analysis)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(getModelName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名坚定的多头分析师，负责从看多角度分析股票的投资价值。

            你的立场：
            - 你是多头阵营的辩手
            - 你需要积极寻找支持买入的证据和逻辑
            - 你相信股价上涨的潜力

            分析框架：
            1. 寻找技术面的利好信号（金叉、突破、缩量整理等）
            2. 分析基本面亮点（业绩增长、政策利好、行业景气等）
            3. 解读舆情中的正面因素
            4. 评估估值吸引力
            5. 识别潜在催化剂

            输出要求：
            - 给出明确的多头观点
            - 提供支持多头的3-5个核心论点
            - 指明潜在上涨空间和目标价
            - 不要忽视风险，但要强调机遇
            """;
    }

    public DebatePosition getPosition() {
        return DebatePosition.BULLISH;
    }

    public int getVoteWeight() {
        return 3;
    }

    private String generateBullishAnalysis(
            MarketDataDTO marketData,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment) {

        StringBuilder analysis = new StringBuilder();
        analysis.append("**【多头观点】坚定看好后市表现**\n\n");

        analysis.append("**核心看多逻辑**:\n\n");

        // 技术面亮点
        analysis.append("1️⃣ **技术面看多**\n");
        TechnicalIndicatorsDTO.MacdDTO macd = technical.getMacd();
        if (macd.getHistogram() > 0) {
            analysis.append("   - MACD红柱持续，趋势向好\n");
        }
        if (technical.getKdj().getK() > technical.getKdj().getD()) {
            analysis.append("   - KDJ金叉形成，短期动能充足\n");
        }
        analysis.append(String.format("   - 综合技术评分 %d/100\n\n", technical.getTechnicalScore()));

        // 基本面亮点
        analysis.append("2️⃣ **基本面支撑**\n");
        analysis.append(String.format("   - 当前价格 ¥%.2f，估值合理\n", marketData.getCurrentPrice()));
        if (marketData.getPe() != null && marketData.getPe() < 30) {
            analysis.append(String.format("   - 市盈率 %.1f，具备估值优势\n", marketData.getPe()));
        }
        analysis.append(String.format("   - 总市值 %.2f亿，流动性良好\n\n", marketData.getMarketCap() / 1e8));

        // 舆情亮点
        analysis.append("3️⃣ **市场情绪偏多**\n");
        if (sentiment.getSentimentScore() > 0.5) {
            analysis.append(String.format("   - 舆情评分 %.0f/100，机构看好\n", sentiment.getSentimentScore() * 100));
        }
        if (!sentiment.getPositiveFactors().isEmpty()) {
            analysis.append("   - 主要利好:\n");
            sentiment.getPositiveFactors().stream()
                    .limit(2)
                    .forEach(f -> analysis.append("     • ").append(f).append("\n"));
        }

        // 目标价
        double targetPrice = marketData.getCurrentPrice() * 1.15;
        double upside = (targetPrice - marketData.getCurrentPrice()) / marketData.getCurrentPrice() * 100;

        analysis.append("\n**目标价格**: ¥").append(String.format("%.2f", targetPrice))
                .append(" (上涨空间: +").append(String.format("%.1f%%", upside)).append(")\n");
        analysis.append("**建议**: 积极买入，回调即是加仓机会");

        return analysis.toString();
    }
}
