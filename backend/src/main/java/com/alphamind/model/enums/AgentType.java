package com.alphamind.model.enums;

public enum AgentType {
    // Pipeline Agents
    MARKET("market", "行情Agent", "负责采集和处理股票行情数据"),
    TECHNICAL("technical", "技术Agent", "负责技术指标分析和图表模式识别"),
    SENTIMENT("sentiment", "舆情Agent", "负责分析市场舆情和情绪"),
    PORTFOLIO("portfolio", "投资Agent", "负责综合分析并生成投资建议"),

    // Debate Agents
    BULL("bull", "多头Agent", "从看多角度分析股票"),
    BEAR("bear", "空头Agent", "从看空角度分析股票"),
    NEUTRAL("neutral", "中立Agent", "保持中立客观分析"),
    ARBITRATOR("arbitrator", "仲裁官", "综合各方观点做出最终裁决");

    private final String code;
    private final String name;
    private final String description;

    AgentType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static AgentType fromCode(String code) {
        for (AgentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown agent code: " + code);
    }
}
