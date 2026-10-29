package com.alphamind.backtest;

/** 事先写定的区分力判定（样本外、h=10）。 */
public enum Verdict {
    DISCRIMINATIVE("有区分力"),
    WEAK("弱/不确定"),
    NONE("未见区分力"),
    INSUFFICIENT_DATA("数据不足，不做判定");

    private final String label;

    Verdict(String label) { this.label = label; }

    public String label() { return label; }
}
