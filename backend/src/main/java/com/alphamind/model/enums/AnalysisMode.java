package com.alphamind.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 分析执行模式
 * PIPELINE: 仅执行 Market -> Technical -> Sentiment -> Portfolio 流水线
 * DEBATE: 在流水线后追加多空辩论与仲裁
 */
public enum AnalysisMode {
    PIPELINE,
    DEBATE;

    @JsonCreator
    public static AnalysisMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AnalysisMode.valueOf(value.trim().toUpperCase());
    }
}
