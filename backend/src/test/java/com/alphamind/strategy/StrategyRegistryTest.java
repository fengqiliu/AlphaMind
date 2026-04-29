package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyRegistryTest {

    private final StrategyRegistry registry = new StrategyRegistry(
            new ConservativeStrategyProfile(),
            new BalancedStrategyProfile(),
            new AggressiveStrategyProfile()
    );

    @Test
    void shouldFallbackToBalancedWhenStrategyTypeIsNull() {
        StrategyProfile profile = registry.resolve(null);

        assertEquals(StrategyType.BALANCED, profile.getType());
        assertEquals(AnalysisMode.DEBATE, profile.getDefaultMode());
    }

    @Test
    void shouldReturnAggressiveProfileWithPipelineDefaultMode() {
        StrategyProfile profile = registry.resolve(StrategyType.AGGRESSIVE);

        assertEquals(StrategyType.AGGRESSIVE, profile.getType());
        assertEquals(AnalysisMode.PIPELINE, profile.getDefaultMode());
        assertEquals(0.10, profile.getStopLossRatio());
    }
}
