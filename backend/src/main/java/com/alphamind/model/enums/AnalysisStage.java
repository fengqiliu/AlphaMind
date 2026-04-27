package com.alphamind.model.enums;

public enum AnalysisStage {
    START("START", "开始分析"),
    MARKET("MARKET", "采集行情数据"),
    TECHNICAL("TECHNICAL", "技术分析"),
    SENTIMENT("SENTIMENT", "舆情分析"),
    PORTFOLIO("PORTFOLIO", "生成投资建议"),
    DEBATE("DEBATE", "多空辩论"),
    COMPLETE("COMPLETE", "分析完成"),
    ERROR("ERROR", "分析失败");

    private final String code;
    private final String label;

    AnalysisStage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
