package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import com.alphamind.strategy.StrategyProfile;
import com.alphamind.strategy.StrategyRegistry;
import com.alphamind.strategy.StrategySignalPlanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 投资Agent - 负责综合分析并生成投资建议
 */
@Slf4j
@Component
public class PortfolioAgent extends BaseAgent {

    private final StrategyRegistry strategyRegistry;
    private final StrategySignalPlanner strategySignalPlanner;

    public PortfolioAgent(StrategyRegistry strategyRegistry, StrategySignalPlanner strategySignalPlanner) {
        super(AgentType.PORTFOLIO);
        this.strategyRegistry = strategyRegistry;
        this.strategySignalPlanner = strategySignalPlanner;
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始生成投资建议: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            TechnicalIndicatorsDTO technicalIndicators = getContext("technicalIndicators");
            SentimentDataDTO sentimentData = getContext("sentimentData");
            StrategyType strategyType = getContext("strategy", StrategyType.BALANCED);
            StrategyProfile strategy = strategyRegistry.resolve(strategyType);

            if (marketData == null || technicalIndicators == null || sentimentData == null) {
                throw new RuntimeException("缺少必要数据");
            }

            // 先生成置信度，再由策略生成交易信号
            ConfidenceDTO confidence = calculateConfidence(technicalIndicators, sentimentData);
            TradeSignalDTO tradeSignal = strategySignalPlanner.plan(
                    marketData.getCurrentPrice(),
                    technicalIndicators.getTechnicalScore(),
                    sentimentData.getSentimentScore(),
                    confidence.getValue(),
                    strategy
            );

            // 用LLM增强投资理由
            String contextSummary = getContext("contextSummary");
            String aiRationale = generateAiRationale(marketData, technicalIndicators, sentimentData, strategy, tradeSignal, contextSummary);
            if (aiRationale != null) {
                tradeSignal.setRationale(aiRationale);
            }

            report.setTradeSignal(tradeSignal);
            report.setFinalSignal(tradeSignal.getType());
            setContext("tradeSignal", tradeSignal);

            report.setConfidence(confidence);
            setContext("confidence", confidence);

            logInfo("投资建议生成完成，信号: " + tradeSignal.getType());
            return report;
        } catch (Exception e) {
            logError("投资建议生成失败", e);
            throw new RuntimeException("投资建议生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        TradeSignalDTO signal = getContext("tradeSignal");
        ConfidenceDTO confidence = getContext("confidence");

        if (signal == null) {
            return buildMsg("暂无投资建议，请先发起分析。");
        }

        // 优先使用LLM
        String portfolioPrompt = String.format("""
                当前投资建议：
                - 信号: %s (%s)
                - 入场价: ¥%.2f | 目标价: ¥%.2f | 止损价: ¥%.2f
                - 持仓周期: %d天
                - 理由: %s

                用户问题：%s
                """,
                signal.getType().getLabel(), com.alphamind.scoring.AnalysisScoringPolicy.describe(confidence),
                signal.getEntryPrice(), signal.getTargetPrice(), signal.getStopLoss(),
                signal.getHoldingPeriodDays(), signal.getRationale(), userMessage.getContent());

        String response = llmCall(getSystemPrompt(), portfolioPrompt);
        if (response == null) {
            response = buildPortfolioTemplate(signal, confidence);
        }

        return buildMsg(response);
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名专业的投资顾问，负责综合各方分析结果，生成最终的投资建议。

            你的职责：
            1. 综合市场行情分析
            2. 结合技术分析结论
            3. 考量舆情和市场情绪
            4. 根据策略类型（保守/平衡/激进）调整仓位、止损与持仓周期
            5. 生成明确的买入/卖出/持有建议
            6. 给出具体的目标价、止损价和建议持仓周期

            信号生成规则：
            - 等权综合分=技术评分×0.5+舆情评分(0–1)×100×0.5
            - 综合分>=70且规则一致度达到传入策略阈值才为买入；[50,70)持有；<50卖出
            - 一致度不足时买入降为持有；不得将一致度解释为收益概率
            - 操作、目标价、止损与周期已由规则确定，只解释不得改写

            止损原则（根据传入的策略参数动态决定）：
            - 保守策略：仓位较低，止损较严，持仓周期较长
            - 平衡策略：仓位适中，止损适中，持仓周期适中
            - 激进策略：仓位较高，止损宽松，持仓周期较短

            回答要求：
            - 给出明确的交易信号（买入/持有/卖出）
            - 结合当前策略参数（仓位比例、止损点位、持仓周期）提供具体建议
            - 用简洁专业的中文解释决策依据
            """;
    }

    private String generateAiRationale(MarketDataDTO market, TechnicalIndicatorsDTO tech,
                                        SentimentDataDTO sentiment, StrategyProfile strategy, TradeSignalDTO signal,
                                        String contextSummary) {
        String prompt = String.format("""
                综合分析数据如下：
                - 股票: %s (%s) 当前价: ¥%.2f 涨跌: %+.2f%%
                - 技术评分: %d/100 | 舆情评分: %.0f/100
                - 策略类型: %s（仓位 %.0f%%，止损 %.0f%%，持仓 %d天）
                - 推荐操作: %s | 目标价: ¥%.2f | 止损价: ¥%.2f

                评分依据与缺口：%s
                请给出简洁专业的投资理由（200字内），不得编造基本面或改写给定操作。
                """,
                market.getStockName(), market.getStockCode(),
                market.getCurrentPrice(), market.getChangePercent() != null ? market.getChangePercent() : 0,
                tech.getTechnicalScore(), sentiment.getSentimentScore() * 100,
                strategy.getLabel(),
                strategy.getPositionRatio() * 100,
                strategy.getStopLossRatio() * 100,
                strategy.getHoldingPeriodDays(),
                signal.getType().getLabel(),
                signal.getTargetPrice(), signal.getStopLoss(),
                com.alphamind.scoring.AnalysisScoringPolicy.confidence(market, tech, sentiment).getExplanation()
                        + "；技术口径：" + tech.getScoreExplanation() + "；舆情口径：" + sentiment.getScoreExplanation()
                        + "；数据缺口：" + sentiment.getDataGaps());
        if (contextSummary != null && !contextSummary.isBlank()) {
            prompt += "\n\n【历史分析参考（仅供参考，不构成决策依据）】\n" + contextSummary;
        }
        return llmCall(getSystemPrompt(), prompt);
    }

    private String buildPortfolioTemplate(TradeSignalDTO signal, ConfidenceDTO confidence) {
        return String.format("""
                **投资建议**

                **交易信号**: %s
                **一致度/历史值**: %s

                **交易计划**:
                - 入场价: ¥%.2f
                - 目标价: ¥%.2f (上涨空间: %+.2f%%)
                - 止损价: ¥%.2f (下跌风险: %.2f%%)
                - 建议持仓周期: %d个交易日

                **投资理由**: %s
                """,
                signal.getType().getLabel(),
                com.alphamind.scoring.AnalysisScoringPolicy.describe(confidence),
                signal.getEntryPrice(),
                signal.getTargetPrice(),
                ((signal.getTargetPrice() - signal.getEntryPrice()) / signal.getEntryPrice()) * 100,
                signal.getStopLoss(),
                ((signal.getEntryPrice() - signal.getStopLoss()) / signal.getEntryPrice()) * 100,
                signal.getHoldingPeriodDays(),
                signal.getRationale());
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

    private ConfidenceDTO calculateConfidence(
            TechnicalIndicatorsDTO technicalIndicators,
            SentimentDataDTO sentimentData) {

        return com.alphamind.scoring.AnalysisScoringPolicy.confidence(
                getContext("marketData"), technicalIndicators, sentimentData);
    }
}
