package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.springframework.stereotype.Component;

@Component
public class ConservativeStrategyProfile implements StrategyProfile {

    @Override
    public StrategyType getType() {
        return StrategyType.CONSERVATIVE;
    }

    @Override
    public String getLabel() {
        return "保守策略";
    }

    @Override
    public double getPositionRatio() {
        return 0.3;
    }

    @Override
    public double getStopLossRatio() {
        return 0.05;
    }

    @Override
    public int getHoldingPeriodDays() {
        return 45;
    }

    @Override
    public double getConfidenceThreshold() {
        return 0.75;
    }

    @Override
    public AnalysisMode getDefaultMode() {
        return AnalysisMode.DEBATE;
    }

    @Override
    public double getBuyTargetBaseRatio() {
        return 0.08;
    }
}
