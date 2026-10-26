package com.alphamind.backtest;

/** 单边交易成本比例。默认：买入佣金 0.025% + 滑点 0.05%；卖出再加印花税 0.05%。 */
public record Costs(double buy, double sell) {
    public static final Costs DEFAULT = new Costs(0.00075, 0.00125);

    public double net(double entry, double exit) {
        return exit * (1 - sell) / (entry * (1 + buy)) - 1;
    }
}
