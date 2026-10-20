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
    void sameScaleBoundariesAndConfidenceGateAreInclusive() {
        StrategyProfile profile = new BalancedStrategyProfile();
        assertEquals(SignalType.BUY, planner.plan(100, 60, 0.8, 0.65, profile).getType());
        assertEquals(SignalType.HOLD, planner.plan(100, 60, 0.799, 0.9, profile).getType());
        assertEquals(SignalType.HOLD, planner.plan(100, 40, 0.6, 0.9, profile).getType());
        assertEquals(SignalType.SELL, planner.plan(100, 40, 0.599, 0.9, profile).getType());
        assertEquals(SignalType.HOLD, planner.plan(100, 80, 0.8, 0.649, profile).getType());
    }

    @Test
    void shouldReturnSellWhenCompositeScoreIsLow() {
        StrategyProfile profile = new BalancedStrategyProfile();

        TradeSignalDTO signal = planner.plan(100.0, 20, 0.1, 0.7, profile);

        assertEquals(SignalType.SELL, signal.getType());
    }
}
