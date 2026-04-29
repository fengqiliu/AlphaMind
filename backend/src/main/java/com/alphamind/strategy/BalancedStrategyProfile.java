package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.springframework.stereotype.Component;

@Component
public class BalancedStrategyProfile implements StrategyProfile {

    @Override
    public StrategyType getType() {
        return StrategyType.BALANCED;
    }

    @Override
    public String getLabel() {
        return "平衡策略";
    }

    @Override
    public double getPositionRatio() {
        return 0.5;
    }

    @Override
    public double getStopLossRatio() {
        return 0.07;
    }

    @Override
    public int getHoldingPeriodDays() {
        return 30;
    }

    @Override
    public double getConfidenceThreshold() {
        return 0.65;
    }

    @Override
    public AnalysisMode getDefaultMode() {
        return AnalysisMode.DEBATE;
    }

    @Override
    public double getBuyTargetBaseRatio() {
        return 0.10;
    }
}
