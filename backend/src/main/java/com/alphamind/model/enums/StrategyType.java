package com.alphamind.model.enums;

public enum StrategyType {
    CONSERVATIVE("conservative", "保守策略", 0.3),
    BALANCED("balanced", "平衡策略", 0.5),
    AGGRESSIVE("aggressive", "激进策略", 0.8);

    private final String code;
    private final String label;
    private final double positionRatio;

    StrategyType(String code, String label, double positionRatio) {
        this.code = code;
        this.label = label;
        this.positionRatio = positionRatio;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public double getPositionRatio() {
        return positionRatio;
    }
}
