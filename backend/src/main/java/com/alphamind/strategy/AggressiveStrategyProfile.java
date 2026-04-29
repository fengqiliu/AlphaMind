package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.springframework.stereotype.Component;

@Component
public class AggressiveStrategyProfile implements StrategyProfile {

    @Override
    public StrategyType getType() {
        return StrategyType.AGGRESSIVE;
    }

    @Override
    public String getLabel() {
        return "激进策略";
    }

    @Override
    public double getPositionRatio() {
        return 0.8;
    }

    @Override
    public double getStopLossRatio() {
        return 0.10;
    }

    @Override
    public int getHoldingPeriodDays() {
        return 15;
    }

    @Override
    public double getConfidenceThreshold() {
        return 0.55;
    }

    @Override
    public AnalysisMode getDefaultMode() {
        return AnalysisMode.PIPELINE;
    }

    @Override
    public double getBuyTargetBaseRatio() {
        return 0.12;
    }
}
