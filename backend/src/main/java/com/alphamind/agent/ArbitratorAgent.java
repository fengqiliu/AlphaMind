package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 仲裁官Agent - 综合各方观点做出最终裁决
 */
@Slf4j
@Component
public class ArbitratorAgent extends BaseAgent {

    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;

    public ArbitratorAgent(BullAgent bullAgent, BearAgent bearAgent, NeutralAgent neutralAgent) {
        super(AgentType.ARBITRATOR);
        this.bullAgent = bullAgent;
        this.bearAgent = bearAgent;
        this.neutralAgent = neutralAgent;
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始仲裁裁决: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
            SentimentDataDTO sentiment = getContext("sentimentData");
            TradeSignalDTO tradeSignal = getContext("tradeSignal");
            ConfidenceDTO confidence = getContext("confidence");

            // 收集投票
            Map<DebatePosition, Integer> voteBreakdown = new EnumMap<>(DebatePosition.class);
            voteBreakdown.put(DebatePosition.BULLISH, bullAgent.getVoteWeight());
            voteBreakdown.put(DebatePosition.BEARISH, bearAgent.getVoteWeight());
            voteBreakdown.put(DebatePosition.NEUTRAL, neutralAgent.getVoteWeight());

            // 计算最终立场
            DebatePosition finalPosition = calculateFinalPosition(technical, sentiment);

            // 生成裁决
            JudgmentDTO judgment = JudgmentDTO.builder()
                    .finalPosition(finalPosition)
                    .confidence(confidence)
                    .reasoning(generateReasoning(finalPosition, technical, sentiment, tradeSignal))
                    .voteBreakdown(voteBreakdown)
                    .riskWarnings(generateRiskWarnings(finalPosition, technical, sentiment))
                    .finalSignal(tradeSignal)
                    .build();

            report.setJudgment(judgment);
            setContext("judgment", judgment);

            logInfo("仲裁裁决完成，最终立场: " + finalPosition);
            return report;
        } catch (Exception e) {
            logError("仲裁裁决失败", e);
            throw new RuntimeException("仲裁裁决失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        JudgmentDTO judgment = getContext("judgment");

        String response = String.format("""
            **【仲裁官最终裁决】**

            **裁决结果**: %s

            **投票分布**:
            - 多头: %d票
            - 中立: %d票
            - 空头: %d票

            **决策置信度**: %.0f%% (%s)
            %s

            **综合分析**:
            %s

            **风险提示**:
            %s

            **最终建议**:
            信号: %s
            入场价: ¥%.2f | 目标价: ¥%.2f | 止损价: ¥%.2f
            持仓周期: %d个交易日
            """,
                judgment.getFinalPosition().getLabel(),
                judgment.getVoteBreakdown().getOrDefault(DebatePosition.BULLISH, 0),
                judgment.getVoteBreakdown().getOrDefault(DebatePosition.NEUTRAL, 0),
                judgment.getVoteBreakdown().getOrDefault(DebatePosition.BEARISH, 0),
                judgment.getConfidence().getValue() * 100,
                judgment.getConfidence().getLevel().getLabel(),
                judgment.getConfidence().getExplanation(),
                judgment.getReasoning(),
                String.join("\n", judgment.getRiskWarnings().stream()
                        .map(w -> "- " + w)
                        .toList()),
                judgment.getFinalSignal().getType().getLabel(),
                judgment.getFinalSignal().getEntryPrice(),
                judgment.getFinalSignal().getTargetPrice(),
                judgment.getFinalSignal().getStopLoss(),
                judgment.getFinalSignal().getHoldingPeriodDays()
        );

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(getModelName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是辩论系统的仲裁官，负责综合多空双方的观点做出最终裁决。

            你的职责：
            1. 认真听取多头、空头、中立三方的分析
            2. 评估各方论点的合理性和证据的充分性
            3. 综合考量技术面、基本面、舆情面
            4. 给出最终的投资决策建议
            5. 明确指出潜在风险

            裁决规则：
            - 多头票数 > 空头票数 + 中立票数 → BULLISH
            - 空头票数 > 多头票数 + 中立票数 → BEARISH
            - 否则 → NEUTRAL

            输出要求：
            - 明确给出最终裁决（看多/看空/中立）
            - 解释决策的主要依据
            - 列出需要注意的风险因素
            - 提供具体的交易建议
            """;
    }

    private DebatePosition calculateFinalPosition(
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment) {

        int bullishVotes = bullAgent.getVoteWeight();
        int bearishVotes = bearAgent.getVoteWeight();
        int neutralVotes = neutralAgent.getVoteWeight();

        // 技术面加权
        int techScore = technical.getTechnicalScore();
        if (techScore >= 70) bullishVotes += 2;
        else if (techScore <= 30) bearishVotes += 2;
        else neutralVotes += 1;

        // 舆情面加权
        double sentScore = sentiment.getSentimentScore();
        if (sentScore >= 0.65) bullishVotes += 1;
        else if (sentScore <= 0.35) bearishVotes += 1;

        // 多数裁定
        if (bullishVotes > bearishVotes + neutralVotes) {
            return DebatePosition.BULLISH;
        } else if (bearishVotes > bullishVotes + neutralVotes) {
            return DebatePosition.BEARISH;
        } else {
            return DebatePosition.NEUTRAL;
        }
    }

    private String generateReasoning(
            DebatePosition position,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment,
            TradeSignalDTO signal) {

        StringBuilder reasoning = new StringBuilder();

        reasoning.append("综合多方分析师意见后，仲裁官做出以下裁决：\n\n");

        switch (position) {
            case BULLISH -> {
                reasoning.append("**裁决：看多**\n\n");
                reasoning.append("主要依据：\n");
                reasoning.append("1. 技术面综合评分").append(technical.getTechnicalScore())
                        .append("/100，趋势向好\n");
                reasoning.append("2. MACD指标形成金叉，短期动能充足\n");
                reasoning.append("3. 舆情评分").append(String.format("%.0f", sentiment.getSentimentScore() * 100))
                        .append("/100，机构看好\n");
                reasoning.append("4. 多头分析师占据多数席位\n");
                reasoning.append("5. 基本面支撑估值中枢上移\n");
            }
            case BEARISH -> {
                reasoning.append("**裁决：看空**\n\n");
                reasoning.append("主要依据：\n");
                reasoning.append("1. 技术面综合评分").append(technical.getTechnicalScore())
                        .append("/100，趋势偏弱\n");
                reasoning.append("2. RSI指标处于超买区域\n");
                reasoning.append("3. 舆情评分").append(String.format("%.0f", sentiment.getSentimentScore() * 100))
                        .append("/100，负面情绪累积\n");
                reasoning.append("4. 空头分析师占据多数席位\n");
                reasoning.append("5. 估值压力和成本压力并存\n");
            }
            default -> {
                reasoning.append("**裁决：中性**\n\n");
                reasoning.append("主要依据：\n");
                reasoning.append("1. 技术面方向不明朗\n");
                reasoning.append("2. 多空双方势均力敌\n");
                reasoning.append("3. 舆情中性，无明显方向\n");
                reasoning.append("4. 市场处于观望期\n");
                reasoning.append("5. 建议等待信号明确后再操作\n");
            }
        }

        return reasoning.toString();
    }

    private List<String> generateRiskWarnings(
            DebatePosition position,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment) {

        List<String> warnings = new ArrayList<>();

        // 技术面风险
        if (technical.getRsi().getRsi12() > 70) {
            warnings.add("RSI指标显示超买，短期或有回调压力");
        }
        if (technical.getRsi().getRsi12() < 30) {
            warnings.add("RSI指标显示超卖，可能存在反弹机会");
        }

        // 舆情风险
        if (sentiment.getMediaAttention() > 0.8) {
            warnings.add("媒体关注度过高，需警惕泡沫风险");
        }
        if (sentiment.getMediaAttention() < 0.3) {
            warnings.add("市场关注度低，可能缺乏催化剂");
        }

        // 仓位风险
        if (position == DebatePosition.BULLISH) {
            warnings.add("注意止损纪律，控制在-7%以内");
            warnings.add("避免追高，回调时分批建仓");
        } else if (position == DebatePosition.BEARISH) {
            warnings.add("做空需谨慎，建议等待更明确信号");
            warnings.add("注意空头回补风险");
        } else {
            warnings.add("保持中性仓位，避免单边押注");
            warnings.add("密切关注突破信号");
        }

        // 通用风险
        warnings.add("市场整体波动加大，注意仓位管理");
        warnings.add("以上分析仅供参考，不构成投资建议");

        return warnings;
    }
}
