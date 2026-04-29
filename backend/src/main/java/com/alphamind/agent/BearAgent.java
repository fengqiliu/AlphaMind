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
        MarketDataDTO market = getContext("marketData");
        TechnicalIndicatorsDTO tech = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String viewText;
        String llmPrompt = buildBearPrompt(market, tech, sentiment, "请从空头立场列出该股主要风险点和潜在下行空间。");
        String llmResult = llmCall(getSystemPrompt(), llmPrompt);
        viewText = llmResult != null ? llmResult : generateBearArgument(market, tech, sentiment);

        List<String> reasons = extractBearReasons(market, tech, sentiment);
        double stopLoss = market != null ? market.getCurrentPrice() * 0.93 : 0;
        double downside = -7.0;
        double confValue = tech != null ? Math.min(0.90, 0.35 + (100 - tech.getTechnicalScore()) / 130.0) : 0.55;
        ConfidenceLevel confLevel = confValue >= 0.70 ? ConfidenceLevel.HIGH
                : confValue >= 0.50 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;

        DebateViewDTO bearView = DebateViewDTO.builder()
                .position(DebatePosition.BEARISH)
                .agentType(AgentType.BEAR)
                .view(viewText)
                .reasons(reasons)
                .keyPoints(reasons.stream().limit(3).toList())
                .targetPrice(stopLoss)          // 空头的目标价为止据价
                .upsidePotential(String.format("%.1f%%", downside))
                .confidence(ConfidenceDTO.builder()
                        .value(confValue)
                        .level(confLevel)
                        .explanation("基于风险指标和市场环境评估")
                        .build())
                .attackPoints(List.of(
                        "多头短期上涨动能将逐渐衰减，风险收益比不划算",
                        "市场整体处于震荡整理期，个股相对弱势",
                        "脆弱点未回应，备下行展开复盘风险"
                ))
                .build();

        setContext("bearView", bearView);
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String prompt = buildBearPrompt(marketData, technical, sentiment, userMessage.getContent());
        String response = llmCall(getSystemPrompt(), prompt);
        if (response == null) {
            response = generateBearArgument(marketData, technical, sentiment);
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

    private String buildBearPrompt(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment, String question) {
        return String.format("""
                股票: %s (%s), 当前价: ¥%.2f, 涨跌: %+.2f%%
                技术评分: %d/100, 舆情评分: %.0f/100
                用户问题: %s
                请从空头立场给出3-5个核心风险点和止损建议。
                """,
                market != null ? market.getStockName() : "N/A",
                market != null ? market.getStockCode() : "N/A",
                market != null ? market.getCurrentPrice() : 0,
                market != null && market.getChangePercent() != null ? market.getChangePercent() : 0,
                tech != null ? tech.getTechnicalScore() : 0,
                sentiment != null ? sentiment.getSentimentScore() * 100 : 0,
                question);
    }

    private List<String> extractBearReasons(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment) {
        List<String> reasons = new ArrayList<>();
        if (tech != null) {
            if (tech.getRsi() != null && tech.getRsi().getRsi12() > 70)
                reasons.add("RSI 超买区域，短期有回调需求");
            if (tech.getKdj() != null && tech.getKdj().getJ() > 90)
                reasons.add("KDJ J值超买，动能可能衰竭");
            if (tech.getMacd() != null && tech.getMacd().getHistogram() < 0)
                reasons.add("MACD绿柱拓展，空头渔口加重");
            if (tech.getTechnicalScore() < 45)
                reasons.add("综合技术评分仅 " + tech.getTechnicalScore() + "/100，属偏弱单个股");
        }
        if (market != null) {
            if (market.getPe() != null && market.getPe() > 50)
                reasons.add("PE " + String.format("%.1f", market.getPe()) + "，高估値压力显著");
            if (market.getChangePercent() != null && market.getChangePercent() > 5)
                reasons.add("近期涨幅过大，获利回吐压力大");
        }
        if (sentiment != null && sentiment.getSentimentScore() < 0.45)
            reasons.add("舆情评分低于 45/100，负面情绪累积");
        if (reasons.isEmpty()) {
            reasons.add("当前价格缺乏向上催化剂，备位调整冨力");
            reasons.add("宏观不确定性增大，和屢观望为优");
        }
        return reasons;
    }

    private String generateBearArgument(MarketDataDTO marketData, TechnicalIndicatorsDTO technical, SentimentDataDTO sentiment) {
        if (marketData == null) return "【空头观点】数据暂不可用，请先发起分析。";

        StringBuilder analysis = new StringBuilder();
        analysis.append("**【空头观点】审慎看待回调风险**\n\n");
        analysis.append("**核心看空逻辑**:\n\n");

        if (technical != null) {
            analysis.append("1️⃣ **技术面承压**\n");
            TechnicalIndicatorsDTO.RsiDTO rsi = technical.getRsi();
            if (rsi != null && rsi.getRsi12() > 70) {
                analysis.append("   - RSI超买区域，短期有调整需求\n");
            }
            if (technical.getKdj() != null && technical.getKdj().getJ() > 90) {
                analysis.append("   - KDJ J值超买，动能可能衰竭\n");
            }
            analysis.append(String.format("   - 综合技术评分 %d/100，需谨慎\n\n", technical.getTechnicalScore()));
        }

        analysis.append("2️⃣ **基本面隐忧**\n");
        if (marketData.getPe() != null && marketData.getPe() > 40) {
            analysis.append(String.format("   - 市盈率 %.1f偏高，估值有压力\n", marketData.getPe()));
        }
        if (marketData.getChangePercent() != null) {
            analysis.append(String.format("   - 近期涨幅%.2f%%，获利回吐压力大\n", marketData.getChangePercent()));
        }
        if (sentiment != null && sentiment.getNegativeFactors() != null && !sentiment.getNegativeFactors().isEmpty()) {
            analysis.append("   - 主要风险:\n");
            sentiment.getNegativeFactors().stream()
                    .limit(2)
                    .forEach(f -> analysis.append("     • ").append(f).append("\n"));
        }

        if (sentiment != null) {
            analysis.append("\n3️⃣ **市场情绪风险**\n");
            if (sentiment.getMediaAttention() != null && sentiment.getMediaAttention() > 0.8) {
                analysis.append("   - 媒体关注度过高，存在泡沫风险\n");
            }
        }

        double stopLoss = marketData.getCurrentPrice() * 0.93;
        double downside = (marketData.getCurrentPrice() - stopLoss) / marketData.getCurrentPrice() * 100;

        analysis.append("\n**止损建议**: ¥").append(String.format("%.2f", stopLoss))
                .append(" (下跌风险: -").append(String.format("%.1f%%", downside)).append(")\n");
        analysis.append("**建议**: 控制仓位，等待更好的买入时机");

        return analysis.toString();
    }
}
