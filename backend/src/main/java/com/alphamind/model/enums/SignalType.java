package com.alphamind.model.enums;

public enum SignalType {
    BUY("买入", "建议买入"),
    SELL("卖出", "建议卖出"),
    HOLD("持有", "建议持有观望");

    private final String label;
    private final String description;

    SignalType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
