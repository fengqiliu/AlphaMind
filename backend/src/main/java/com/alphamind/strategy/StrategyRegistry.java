package com.alphamind.strategy;

import com.alphamind.model.enums.StrategyType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 策略注册表
 */
@Service
public class StrategyRegistry {

    private final Map<StrategyType, StrategyProfile> profiles = new EnumMap<>(StrategyType.class);

    public StrategyRegistry(
            ConservativeStrategyProfile conservative,
            BalancedStrategyProfile balanced,
            AggressiveStrategyProfile aggressive) {
        profiles.put(StrategyType.CONSERVATIVE, conservative);
        profiles.put(StrategyType.BALANCED, balanced);
        profiles.put(StrategyType.AGGRESSIVE, aggressive);
    }

    public StrategyProfile resolve(StrategyType strategyType) {
        if (strategyType == null) {
            return profiles.get(StrategyType.BALANCED);
        }
        return profiles.getOrDefault(strategyType, profiles.get(StrategyType.BALANCED));
    }
}
