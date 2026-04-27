package com.alphamind.model.enums;

public enum ConfidenceLevel {
    HIGH("高置信度", 0.7, 1.0),
    MEDIUM("中置信度", 0.4, 0.7),
    LOW("低置信度", 0.0, 0.4);

    private final String label;
    private final double minValue;
    private final double maxValue;

    ConfidenceLevel(String label, double minValue, double maxValue) {
        this.label = label;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public String getLabel() {
        return label;
    }

    public static ConfidenceLevel fromValue(double value) {
        for (ConfidenceLevel level : values()) {
            if (value >= level.minValue && value <= level.maxValue) {
                return level;
            }
        }
        return LOW;
    }
}
