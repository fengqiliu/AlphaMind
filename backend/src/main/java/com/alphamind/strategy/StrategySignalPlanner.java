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

        if (!Double.isFinite(currentPrice) || currentPrice <= 0 || !Double.isFinite(confidenceValue)
                || confidenceValue < 0 || confidenceValue > 1) throw new IllegalArgumentException("价格或一致度无效");
        double compositeScore = com.alphamind.scoring.AnalysisScoringPolicy.composite(technicalScore, sentimentScore);
        SignalType signalType;
        double targetRatio;
        String rationale;

        if (compositeScore >= 70) {
            signalType = SignalType.BUY;
            targetRatio = strategy.getBuyTargetBaseRatio() + (compositeScore - 70) / 200.0;
            rationale = "技术与舆情按0–100等权合成，达到70分规则阈值；示意交易计划未经回测校准。";
        } else if (compositeScore >= 50) {
            signalType = SignalType.HOLD;
            targetRatio = 0.03;
            rationale = "等权综合评分处于50至不足70分，按规则观察。";
        } else {
            signalType = SignalType.SELL;
            targetRatio = -0.05;
            rationale = "等权综合评分不足50分，按规则控制风险。";
        }

        if (signalType == SignalType.BUY && confidenceValue < strategy.getConfidenceThreshold()) {
            signalType = SignalType.HOLD;
            rationale = String.format("规则一致度 %.0f/100 低于%s阈值 %.0f/100，转为观望。",
                    confidenceValue * 100, strategy.getLabel(), strategy.getConfidenceThreshold() * 100);
            targetRatio = 0.02;
        }

        rationale += String.format(java.util.Locale.ROOT, " 等权综合%.1f/100，技术%d/100、舆情%.0f/100；一致度%.0f/100，%s门槛%.0f/100。目标价与止损为策略示意参数，不是价格预测。",
                compositeScore, technicalScore, sentimentScore * 100, confidenceValue * 100,
                strategy.getLabel(), strategy.getConfidenceThreshold() * 100);
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
