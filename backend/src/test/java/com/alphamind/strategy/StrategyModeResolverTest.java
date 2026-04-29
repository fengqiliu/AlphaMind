package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyModeResolverTest {

    private final StrategyModeResolver resolver = new StrategyModeResolver(
            new StrategyRegistry(
                    new ConservativeStrategyProfile(),
                    new BalancedStrategyProfile(),
                    new AggressiveStrategyProfile()
            )
    );

    @Test
    void shouldUseExplicitModeWhenProvided() {
        AnalysisMode mode = resolver.resolve(AnalysisMode.DEBATE, null, StrategyType.AGGRESSIVE);

        assertEquals(AnalysisMode.DEBATE, mode);
    }

    @Test
    void shouldUseLegacyEnableDebateWhenProvided() {
        AnalysisMode mode = resolver.resolve(null, false, StrategyType.CONSERVATIVE);

        assertEquals(AnalysisMode.PIPELINE, mode);
    }

    @Test
    void shouldUseStrategyDefaultModeWhenNoOverrides() {
        AnalysisMode mode = resolver.resolve(null, null, StrategyType.AGGRESSIVE);

        assertEquals(AnalysisMode.PIPELINE, mode);
    }
}
