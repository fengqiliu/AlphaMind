package com.alphamind.strategy;

import com.alphamind.model.enums.StrategyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategyTypeTest {

    @Test
    void strategyTypeEnumShouldHaveThreeValues() {
        // CONSERVATIVE, BALANCED, AGGRESSIVE
        assertEquals(3, StrategyType.values().length);
    }
}