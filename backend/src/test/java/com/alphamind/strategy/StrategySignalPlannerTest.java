package com.alphamind.strategy;

import com.alphamind.model.dto.TradeSignalDTO;
import com.alphamind.model.enums.SignalType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategySignalPlannerTest {

    private final StrategySignalPlanner planner = new StrategySignalPlanner();

    @Test
    void shouldDowngradeBuyToHoldWhenConfidenceIsBelowThreshold() {
        StrategyProfile profile = new ConservativeStrategyProfile();

        TradeSignalDTO signal = planner.plan(100.0, 85, 0.9, 0.6, profile);

        assertEquals(SignalType.HOLD, signal.getType());
        assertEquals(95.0, signal.getStopLoss());
        assertEquals(45, signal.getHoldingPeriodDays());
    }

    @Test
    void shouldUseStrategyRiskParametersWhenSignalIsBuy() {
        StrategyProfile profile = new AggressiveStrategyProfile();

        TradeSignalDTO signal = planner.plan(100.0, 95, 1.0, 0.8, profile);

        assertEquals(SignalType.BUY, signal.getType());
        assertEquals(90.0, signal.getStopLoss());
        assertEquals(15, signal.getHoldingPeriodDays());
    }

    @Test
    void shouldReturnSellWhenCompositeScoreIsLow() {
        StrategyProfile profile = new BalancedStrategyProfile();

        TradeSignalDTO signal = planner.plan(100.0, 20, 0.1, 0.7, profile);

        assertEquals(SignalType.SELL, signal.getType());
    }
}
