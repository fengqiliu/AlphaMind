package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import com.alphamind.scoring.AnalysisScoringPolicy;
import com.alphamind.scoring.DebateEvidence;
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

            // 读取三方辩论观点（由 DebateOrchestrator 注入）
            DebateViewDTO bullView   = getContext("bullView");
            DebateViewDTO bearView   = getContext("bearView");
            DebateViewDTO neutralView = getContext("neutralView");

            Map<DebatePosition, Integer> voteBreakdown = calculateVoteBreakdown(technical, sentiment);
            DebatePosition finalPosition = calculateFinalPosition(voteBreakdown);

            // 生成裁决推理（优先 LLM + 三方观点，降级为模板）
            String reasoning = generateReasoningWithViews(
                    finalPosition, technical, sentiment, tradeSignal,
                    bullView, bearView, neutralView);

            // 生成裁决
            JudgmentDTO judgment = JudgmentDTO.builder()
                    .finalPosition(finalPosition)
                    .confidence(confidence)
                    .reasoning(reasoning)
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

        if (judgment == null) {
            return ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("assistant")
                    .content("暂无仲裁结论，请先对当前股票发起一次辩论模式分析。")
                    .agentType(agentType)
                    .agentName(agentType.getName())
                    .modelUsed(getModelName())
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
        }

        String response = String.format("""
            **【仲裁官最终裁决】**

            **裁决结果**: %s

            **投票分布**:
            - 多头: %d票
            - 中立: %d票
            - 空头: %d票

            **一致度/历史值**: %s
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
                AnalysisScoringPolicy.describe(judgment.getConfidence()),
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
            - 不编造财报、机构观点、交叉、趋势或估值事实；只解释给定证据
            - 仲裁立场不改写策略交易计划，不另行设定目标价和止损
            - 规则一致度不是收益概率
            """;
    }

    Map<DebatePosition, Integer> calculateVoteBreakdown(TechnicalIndicatorsDTO technical, SentimentDataDTO sentiment) {
        Map<DebatePosition, Integer> votes = new EnumMap<>(DebatePosition.class);
        votes.put(DebatePosition.BULLISH, bullAgent.getVoteWeight());
        votes.put(DebatePosition.BEARISH, bearAgent.getVoteWeight());
        votes.put(DebatePosition.NEUTRAL, neutralAgent.getVoteWeight());
        votes.merge(AnalysisScoringPolicy.technicalPosition(technical.getTechnicalScore()), 2, Integer::sum);
        double score = sentiment.getSentimentScore();
        DebatePosition sentimentPosition = score >= 0.65 ? DebatePosition.BULLISH
                : score < 0.35 ? DebatePosition.BEARISH : DebatePosition.NEUTRAL;
        votes.merge(sentimentPosition, 1, Integer::sum);
        return votes;
    }

    DebatePosition calculateFinalPosition(Map<DebatePosition, Integer> votes) {
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        if (votes.get(DebatePosition.BULLISH) * 2 > total) return DebatePosition.BULLISH;
        if (votes.get(DebatePosition.BEARISH) * 2 > total) return DebatePosition.BEARISH;
        return DebatePosition.NEUTRAL;
    }

    private String generateReasoningWithViews(
            DebatePosition position,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment,
            TradeSignalDTO signal,
            DebateViewDTO bullView,
            DebateViewDTO bearView,
            DebateViewDTO neutralView) {

        if (isLlmAvailable() && bullView != null && bearView != null) {
            String prompt = String.format("""
                    以下是三位分析师的辩论内容，请作为仲裁官综合判断，给出最终裁决理由（200字以内，专业简洁）：

                    【多头观点】
                    %s
                    核心论点：%s

                    【空头观点】
                    %s
                    核心论点：%s

                    【中立评估】
                    %s

                    综合投票结果：%s
                    独立策略计划：%s，示意目标价¥%.2f，止损价¥%.2f

                    请给出裁决理由：
                    """,
                    truncate(bullView.getView(), 300),
                    String.join("；", bullView.getReasons()),
                    truncate(bearView.getView(), 300),
                    String.join("；", bearView.getReasons()),
                    neutralView != null ? truncate(neutralView.getView(), 200) : "中立方缺席",
                    position.getLabel(),
                    signal != null ? signal.getType().getLabel() : "持有",
                    signal != null ? signal.getTargetPrice() : 0.0,
                    signal != null ? signal.getStopLoss() : 0.0);

            String llmResult = llmCall(getSystemPrompt(), appendNewsHeadlines(prompt, sentiment));
            if (llmResult != null) return llmResult;
        }

        return generateReasoning(position, technical, sentiment, signal);
    }

    /** 截断过长文本，避免 Prompt 超出 token 限制 */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String generateReasoning(
            DebatePosition position,
            TechnicalIndicatorsDTO technical,
            SentimentDataDTO sentiment,
            TradeSignalDTO signal) {

        Map<DebatePosition, Integer> votes = calculateVoteBreakdown(technical, sentiment);
        return "**规则仲裁：" + position.getLabel() + "**\n\n"
                + "三方基础各1票，技术桶2票、舆情桶1票；总计6票，严格过半才看多/看空，否则中立。\n"
                + "实际票数：看多" + votes.get(DebatePosition.BULLISH) + "、看空" + votes.get(DebatePosition.BEARISH)
                + "、中立" + votes.get(DebatePosition.NEUTRAL) + "。\n"
                + String.join("\n", DebateEvidence.reasons(getContext("marketData"), technical, sentiment, DebatePosition.NEUTRAL))
                + "\n仲裁立场用于核对论据，不改写独立策略交易计划；规则一致度不是收益概率。";
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
            warnings.add("RSI处于超卖区间，不能据此保证反弹");
        }

        // 舆情风险
        // 当前无实测媒体关注度；旧版估算也不能用于判断泡沫或催化剂。
        warnings.add("媒体关注度缺少实测来源，相关风险未评估");

        if (sentiment.getDataGaps() != null) warnings.addAll(sentiment.getDataGaps());
        warnings.add("规则评分与一致度未经历史回测校准，不代表胜率或收益概率");
        warnings.add("止损、仓位和周期以本次策略交易计划为准，不由辩论立场另行设定");

        return warnings;
    }
}
