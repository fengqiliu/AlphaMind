package com.alphamind.strategy;

import com.alphamind.model.dto.TradeSignalDTO;
import com.alphamind.model.enums.SignalType;
import org.springframework.stereotype.Component;

/**
 * 基于策略参数生成交易计划
 */
@Component
public class StrategySignalPlanner {

    public TradeSignalDTO plan(
            double currentPrice,
            int technicalScore,
            double sentimentScore,
            double confidenceValue,
            StrategyProfile strategy) {

        double compositeScore = technicalScore * 0.5 + sentimentScore * 50 * 0.5;
        SignalType signalType;
        double targetRatio;
        String rationale;

        if (compositeScore >= 70) {
            signalType = SignalType.BUY;
            targetRatio = strategy.getBuyTargetBaseRatio() + (compositeScore - 70) / 200.0;
            rationale = "技术面与舆情共振，满足买入条件。";
        } else if (compositeScore >= 50) {
            signalType = SignalType.HOLD;
            targetRatio = 0.03;
            rationale = "信号中性，建议继续观察。";
        } else {
            signalType = SignalType.SELL;
            targetRatio = -0.05;
            rationale = "综合评分偏弱，建议控制风险。";
        }

        if (signalType == SignalType.BUY && confidenceValue < strategy.getConfidenceThreshold()) {
            signalType = SignalType.HOLD;
            rationale = String.format("置信度 %.0f%% 低于%s阈值 %.0f%%，转为观望。",
                    confidenceValue * 100, strategy.getLabel(), strategy.getConfidenceThreshold() * 100);
            targetRatio = 0.02;
        }

        double targetPrice = currentPrice * (1 + targetRatio);
        double stopLossPrice = currentPrice * (1 - strategy.getStopLossRatio());

        return TradeSignalDTO.builder()
                .type(signalType)
                .entryPrice(round(currentPrice))
                .targetPrice(round(targetPrice))
                .stopLoss(round(stopLossPrice))
                .holdingPeriodDays(strategy.getHoldingPeriodDays())
                .rationale(rationale)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
