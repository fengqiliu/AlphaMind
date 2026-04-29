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
        MarketDataDTO market = getContext("marketData");
        TechnicalIndicatorsDTO tech = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        // 优先尝试 LLM，退而使用模板
        String viewText;
        String llmPrompt = buildBullPrompt(market, tech, sentiment, "请从多头立场给出该股的核心看多论点和目标价格。");
        String llmResult = llmCall(getSystemPrompt(), llmPrompt);
        viewText = llmResult != null ? llmResult : generateBullArgument(market, tech, sentiment);

        List<String> reasons = extractBullReasons(market, tech, sentiment);
        double targetPrice = market != null ? market.getCurrentPrice() * 1.15 : 0;
        double upside = 15.0;
        double confValue = tech != null ? Math.min(0.92, 0.40 + tech.getTechnicalScore() / 120.0) : 0.60;
        ConfidenceLevel confLevel = confValue >= 0.70 ? ConfidenceLevel.HIGH
                : confValue >= 0.50 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;

        DebateViewDTO bullView = DebateViewDTO.builder()
                .position(DebatePosition.BULLISH)
                .agentType(AgentType.BULL)
                .view(viewText)
                .reasons(reasons)
                .keyPoints(reasons.stream().limit(3).toList())
                .targetPrice(targetPrice)
                .upsidePotential(String.format("+%.1f%%", upside))
                .confidence(ConfidenceDTO.builder()
                        .value(confValue)
                        .level(confLevel)
                        .explanation("基于技术面和舆情综合评估")
                        .build())
                .attackPoints(List.of(
                        "即便短期回调，中长期上涨逻辑依然成立",
                        "空头所指风险已在现价中充分定价",
                        "当前位置属于中期起点区间，不适合做空"
                ))
                .build();

        setContext("bullView", bullView);
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String prompt = buildBullPrompt(marketData, technical, sentiment, userMessage.getContent());
        String response = llmCall(getSystemPrompt(), prompt);
        if (response == null) {
            response = generateBullArgument(marketData, technical, sentiment);
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

    private String buildBullPrompt(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment, String question) {
        return String.format("""
                股票: %s (%s), 当前价: ¥%.2f, 涨跌: %+.2f%%
                技术评分: %d/100, 舆情评分: %.0f/100
                用户问题: %s
                请从多头立场给出3-5个核心看多论点和目标价。
                """,
                market != null ? market.getStockName() : "N/A",
                market != null ? market.getStockCode() : "N/A",
                market != null ? market.getCurrentPrice() : 0,
                market != null && market.getChangePercent() != null ? market.getChangePercent() : 0,
                tech != null ? tech.getTechnicalScore() : 0,
                sentiment != null ? sentiment.getSentimentScore() * 100 : 0,
                question);
    }

    private List<String> extractBullReasons(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment) {
        List<String> reasons = new ArrayList<>();
        if (tech != null) {
            if (tech.getMacd() != null && tech.getMacd().getHistogram() > 0)
                reasons.add("MACD红柱持续放大，多头趋势确认");
            if (tech.getKdj() != null && tech.getKdj().getK() > tech.getKdj().getD())
                reasons.add("KDJ金叉形成，短期动能充足");
            if (tech.getRsi() != null && tech.getRsi().getRsi12() < 65 && tech.getRsi().getRsi12() > 40)
                reasons.add("RSI 处于健康多头区间，进入时机尚佳");
            if (tech.getTechnicalScore() >= 60)
                reasons.add("综合技术评分 " + tech.getTechnicalScore() + "/100，超越多数股票");
        }
        if (market != null && market.getPe() != null && market.getPe() > 0 && market.getPe() < 35)
            reasons.add("PE " + String.format("%.1f", market.getPe()) + "，估値仍具吸引力");
        if (sentiment != null && sentiment.getSentimentScore() > 0.55)
            reasons.add("舆情评分 " + String.format("%.0f", sentiment.getSentimentScore() * 100) + "/100，机构持续乐观");
        if (reasons.isEmpty()) {
            reasons.add("当前价格处于合理区间，具备上涨潜力");
            reasons.add("市场整体环境有利，板块轮动进入强势");
        }
        return reasons;
    }

    private String generateBullArgument(MarketDataDTO marketData, TechnicalIndicatorsDTO technical, SentimentDataDTO sentiment) {
        if (marketData == null) return "【多头观点】数据暂不可用，请先发起分析。";

        StringBuilder analysis = new StringBuilder();
        analysis.append("**【多头观点】坚定看好后市表现**\n\n");
        analysis.append("**核心看多逻辑**:\n\n");

        if (technical != null) {
            analysis.append("1️⃣ **技术面看多**\n");
            TechnicalIndicatorsDTO.MacdDTO macd = technical.getMacd();
            if (macd != null && macd.getHistogram() > 0) {
                analysis.append("   - MACD红柱持续，趋势向好\n");
            }
            if (technical.getKdj() != null && technical.getKdj().getK() > technical.getKdj().getD()) {
                analysis.append("   - KDJ金叉形成，短期动能充足\n");
            }
            analysis.append(String.format("   - 综合技术评分 %d/100\n\n", technical.getTechnicalScore()));
        }

        analysis.append("2️⃣ **基本面支撑**\n");
        analysis.append(String.format("   - 当前价格 ¥%.2f，估值合理\n", marketData.getCurrentPrice()));
        if (marketData.getPe() != null && marketData.getPe() < 30) {
            analysis.append(String.format("   - 市盈率 %.1f，具备估值优势\n", marketData.getPe()));
        }
        if (marketData.getMarketCap() != null) {
            analysis.append(String.format("   - 总市值 %.2f亿，流动性良好\n\n", marketData.getMarketCap() / 1e8));
        }

        if (sentiment != null) {
            analysis.append("3️⃣ **市场情绪偏多**\n");
            if (sentiment.getSentimentScore() > 0.5) {
                analysis.append(String.format("   - 舆情评分 %.0f/100，机构看好\n", sentiment.getSentimentScore() * 100));
            }
            if (sentiment.getPositiveFactors() != null && !sentiment.getPositiveFactors().isEmpty()) {
                analysis.append("   - 主要利好:\n");
                sentiment.getPositiveFactors().stream()
                        .limit(2)
                        .forEach(f -> analysis.append("     • ").append(f).append("\n"));
            }
        }

        double targetPrice = marketData.getCurrentPrice() * 1.15;
        double upside = (targetPrice - marketData.getCurrentPrice()) / marketData.getCurrentPrice() * 100;

        analysis.append("\n**目标价格**: ¥").append(String.format("%.2f", targetPrice))
                .append(" (上涨空间: +").append(String.format("%.1f%%", upside)).append(")\n");
        analysis.append("**建议**: 积极买入，回调即是加仓机会");

        return analysis.toString();
    }
}
