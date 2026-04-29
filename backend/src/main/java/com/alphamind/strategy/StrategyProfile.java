package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;

/**
 * 策略配置抽象
 */
public interface StrategyProfile {

    StrategyType getType();

    String getLabel();

    double getPositionRatio();

    double getStopLossRatio();

    int getHoldingPeriodDays();

    double getConfidenceThreshold();

    AnalysisMode getDefaultMode();

    double getBuyTargetBaseRatio();
}
