package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.DebatePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 空头Agent - 从看空角度分析股票
 */
@Slf4j
@Component
public class BearAgent extends BaseAgent {

    public BearAgent() {
        super(AgentType.BEAR);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始空头分析: " + report.getStockCode());
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String analysis = generateBearishAnalysis(marketData, technical, sentiment);

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
            你是一名审慎的空头分析师，负责从看空角度分析股票的风险因素。

            你的立场：
            - 你是空头阵营的辩手
            - 你需要冷静识别潜在风险和利空因素
            - 你相信风险防范比盈利更重要

            分析框架：
            1. 识别技术面的警示信号（死叉、破位、超买等）
            2. 分析基本面的风险点（业绩下滑、估值过高、政策利空等）
            3. 解读舆情中的负面因素
            4. 评估市场情绪的过度乐观
            5. 识别潜在的黑天鹅事件

            输出要求：
            - 给出明确的空头观点
            - 提供支持看空的3-5个核心风险点
            - 指明潜在下跌空间和止损位
            - 不要忽视机会，但要强调风险
            """;
    }

    public DebatePosition getPosition() {
        return DebatePosition.BEARISH;
    }

    public int getVoteWeight() {
        return 3;
    }

    private String generateBearishAnalysis(
            MarketDataDTO marketData,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment) {

        StringBuilder analysis = new StringBuilder();
        analysis.append("**【空头观点】审慎看待回调风险**\n\n");

        analysis.append("**核心看空逻辑**:\n\n");

        // 技术面风险
        analysis.append("1️⃣ **技术面承压**\n");
        TechnicalIndicatorsDTO.MacdDTO macd = technical.getMacd();
        TechnicalIndicatorsDTO.RsiDTO rsi = technical.getRsi();
        if (rsi.getRsi12() > 70) {
            analysis.append("   - RSI超买区域，短期有调整需求\n");
        }
        if (technical.getKdj().getJ() > 90) {
            analysis.append("   - KDJ J值超买，动能可能衰竭\n");
        }
        analysis.append(String.format("   - 综合技术评分 %d/100，需谨慎\n\n", technical.getTechnicalScore()));

        // 基本面风险
        analysis.append("2️⃣ **基本面隐忧**\n");
        if (marketData.getPe() != null && marketData.getPe() > 40) {
            analysis.append(String.format("   - 市盈率 %.1f偏高，估值有压力\n", marketData.getPe()));
        }
        analysis.append(String.format("   - 近期涨幅%.2f%%，获利回吐压力大\n", marketData.getChangePercent()));
        if (!sentiment.getNegativeFactors().isEmpty()) {
            analysis.append("   - 主要风险:\n");
            sentiment.getNegativeFactors().stream()
                    .limit(2)
                    .forEach(f -> analysis.append("     • ").append(f).append("\n"));
        }

        // 舆情风险
        analysis.append("\n3️⃣ **市场情绪风险**\n");
        if (sentiment.getMediaAttention() > 0.8) {
            analysis.append("   - 媒体关注度过高，存在泡沫风险\n");
        }

        // 止损价
        double stopLoss = marketData.getCurrentPrice() * 0.93;
        double downside = (marketData.getCurrentPrice() - stopLoss) / marketData.getCurrentPrice() * 100;

        analysis.append("\n**止损建议**: ¥").append(String.format("%.2f", stopLoss))
                .append(" (下跌风险: -").append(String.format("%.1f%%", downside)).append(")\n");
        analysis.append("**建议**: 控制仓位，等待更好的买入时机");

        return analysis.toString();
    }
}
