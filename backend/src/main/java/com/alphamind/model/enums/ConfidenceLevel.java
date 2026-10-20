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
        if (!Double.isFinite(value) || value < 0 || value > 1) return LOW;
        return value >= 0.7 ? HIGH : value >= 0.4 ? MEDIUM : LOW;
    }
}
