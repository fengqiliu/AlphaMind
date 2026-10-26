package com.alphamind.backtest;

/** 横截面秩相关：{@code dates} 为可计算 IC 的交易日数，t 值用全部 IC 的均值，标准误按有效独立样本数（交易日数 / h）计算。 */
public record IcStats(int dates, Double meanIc, Double positiveShare, Double tOverlapAdjusted) {}
