package com.alphamind.model.enums;

public enum DebatePosition {
    BULLISH("看多"),
    BEARISH("看空"),
    NEUTRAL("中立");

    private final String label;

    DebatePosition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
