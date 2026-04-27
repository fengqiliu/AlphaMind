package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 投资Agent - 负责综合分析并生成投资建议
 */
@Slf4j
@Component
public class PortfolioAgent extends BaseAgent {

    public PortfolioAgent() {
        super(AgentType.PORTFOLIO);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始生成投资建议: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            TechnicalIndicatorsDTO technicalIndicators = getContext("technicalIndicators");
            SentimentDataDTO sentimentData = getContext("sentimentData");
            StrategyType strategy = getContext("strategy", StrategyType.BALANCED);

            if (marketData == null || technicalIndicators == null || sentimentData == null) {
                throw new RuntimeException("缺少必要数据");
            }

            // 生成交易信号
            TradeSignalDTO tradeSignal = generateTradeSignal(
                    marketData, technicalIndicators, sentimentData, strategy);

            report.setTradeSignal(tradeSignal);

            // 生成置信度
            ConfidenceDTO confidence = calculateConfidence(
                    technicalIndicators, sentimentData, strategy);

            report.setConfidence(confidence);

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

        String response = String.format("""
            **投资建议**

            **交易信号**: %s
            **置信度**: %.0f%% (%s)

            **交易计划**:
            - 入场价: ¥%.2f
            - 目标价: ¥%.2f (上涨空间: %+.2f%%)
            - 止损价: ¥%.2f (下跌风险: %.2f%%)
            - 建议持仓周期: %d个交易日

            **投资理由**:
            %s
            """,
                signal.getType().getLabel(),
                confidence.getValue() * 100,
                confidence.getLevel().getLabel(),
                signal.getEntryPrice(),
                signal.getTargetPrice(),
                ((signal.getTargetPrice() - signal.getEntryPrice()) / signal.getEntryPrice()) * 100,
                signal.getStopLoss(),
                ((signal.getEntryPrice() - signal.getStopLoss()) / signal.getEntryPrice()) * 100,
                signal.getHoldingPeriodDays(),
                signal.getRationale()
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
            你是一名专业的投资顾问，负责综合各方分析结果，生成最终的投资建议。

            你的职责：
            1. 综合市场行情分析
            2. 结合技术分析结论
            3. 考量舆情和市场情绪
            4. 根据策略类型调整建议
            5. 生成明确的买入/卖出/持有建议
            6. 给出具体的目标价、止损价和持仓周期

            信号生成规则：
            - 技术评分>70 + 舆情>60 → 买入
            - 技术评分<40 + 舆情<40 → 卖出
            - 其他情况 → 持有观望

            止损原则：
            - 保守策略: 止损设在-5%
            - 平衡策略: 止损设在-7%
            - 激进策略: 止损设在-10%

            回答要求：
            - 给出明确的交易信号
            - 提供具体的价格和仓位建议
            - 解释决策依据
            """;
    }

    private TradeSignalDTO generateTradeSignal(
            MarketDataDTO marketData,
            TechnicalIndicatorsDTO technicalIndicators,
            SentimentDataDTO sentimentData,
            StrategyType strategy) {

        double currentPrice = marketData.getCurrentPrice();
        int techScore = technicalIndicators.getTechnicalScore();
        double sentimentScore = sentimentData.getSentimentScore();

        // 计算综合评分
        double compositeScore = techScore * 0.5 + sentimentScore * 50 * 0.5;

        SignalType signalType;
        double targetRatio;
        double stopLossRatio;
        int holdingDays;
        String rationale;

        if (compositeScore >= 70) {
            signalType = SignalType.BUY;
            targetRatio = 0.10 + (compositeScore - 70) / 100;
            rationale = "技术面与技术指标形成共振，舆情偏正面，建议积极布局。";
        } else if (compositeScore >= 50) {
            signalType = SignalType.HOLD;
            targetRatio = 0.05;
            rationale = "技术面与舆情中性，建议观望等待更好买点。";
        } else {
            signalType = SignalType.SELL;
            targetRatio = -0.05;
            rationale = "技术面走弱，舆情偏负面，建议减仓或止损。";
        }

        // 根据策略调整止损比例
        switch (strategy) {
            case CONSERVATIVE -> stopLossRatio = 0.05;
            case AGGRESSIVE -> stopLossRatio = 0.10;
            default -> stopLossRatio = 0.07;
        }

        // 根据策略调整持仓周期
        switch (strategy) {
            case CONSERVATIVE -> holdingDays = 45;
            case AGGRESSIVE -> holdingDays = 15;
            default -> holdingDays = 30;
        }

        double targetPrice = currentPrice * (1 + targetRatio);
        double stopLossPrice = currentPrice * (1 - stopLossRatio);

        return TradeSignalDTO.builder()
                .type(signalType)
                .entryPrice(currentPrice)
                .targetPrice(Math.round(targetPrice * 100) / 100.0)
                .stopLoss(Math.round(stopLossPrice * 100) / 100.0)
                .holdingPeriodDays(holdingDays)
                .rationale(rationale)
                .build();
    }

    private ConfidenceDTO calculateConfidence(
            TechnicalIndicatorsDTO technicalIndicators,
            SentimentDataDTO sentimentData,
            StrategyType strategy) {

        double techScore = technicalIndicators.getTechnicalScore();
        double sentimentScore = sentimentData.getSentimentScore();

        // 计算置信度 (综合评分 + 策略调整)
        double baseConfidence = (techScore / 100.0 * 0.6 + sentimentScore * 0.4);
        double strategyAdjustment = strategy.getPositionRatio() * 0.1;
        double confidenceValue = Math.min(0.95, baseConfidence + strategyAdjustment);

        ConfidenceLevel level = ConfidenceLevel.fromValue(confidenceValue);

        String explanation;
        if (confidenceValue >= 0.7) {
            explanation = String.format("多信号共振，置信度较高，建议%.0f%%仓位",
                    strategy.getPositionRatio() * 100);
        } else if (confidenceValue >= 0.4) {
            explanation = String.format("信号中等，建议%.0f%%仓位，注意控制风险",
                    strategy.getPositionRatio() * 70);
        } else {
            explanation = "信号较弱，建议观望或轻仓参与";
        }

        return ConfidenceDTO.builder()
                .value(Math.round(confidenceValue * 100) / 100.0)
                .level(level)
                .explanation(explanation)
                .build();
    }
}
