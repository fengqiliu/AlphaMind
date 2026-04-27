package com.alphamind.config;

import com.alphamind.model.enums.StrategyType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * 支持前端传入小写策略类型字符串（如 "balanced"）自动转换为枚举
 */
@Component
public class StrategyTypeConverter implements Converter<String, StrategyType> {

    @Override
    public StrategyType convert(String source) {
        try {
            return StrategyType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的策略类型: " + source + "，支持: CONSERVATIVE, BALANCED, AGGRESSIVE");
        }
    }
}
