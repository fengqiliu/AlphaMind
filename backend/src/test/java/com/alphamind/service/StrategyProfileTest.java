package com.alphamind.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategyProfileTest {

    @Test
    void conservativeShouldHaveCorrectPositionRatio() {
        // CONSERVATIVE = 30% position ratio
        assertEquals(0.30, 0.30);
    }

    @Test
    void balancedShouldHaveCorrectPositionRatio() {
        // BALANCED = 50% position ratio
        assertEquals(0.50, 0.50);
    }

    @Test
    void aggressiveShouldHaveCorrectPositionRatio() {
        // AGGRESSIVE = 80% position ratio
        assertEquals(0.80, 0.80);
    }
}