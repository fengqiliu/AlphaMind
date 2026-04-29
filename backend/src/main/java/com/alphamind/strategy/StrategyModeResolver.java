package com.alphamind.strategy;

import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一处理 mode / enableDebate / strategy 的优先级
 */
@Service
@RequiredArgsConstructor
public class StrategyModeResolver {

    private final StrategyRegistry strategyRegistry;

    public AnalysisMode resolve(AnalysisMode mode, Boolean enableDebate, StrategyType strategyType) {
        if (mode != null) {
            return mode;
        }
        if (enableDebate != null) {
            return enableDebate ? AnalysisMode.DEBATE : AnalysisMode.PIPELINE;
        }
        return strategyRegistry.resolve(strategyType).getDefaultMode();
    }
}
