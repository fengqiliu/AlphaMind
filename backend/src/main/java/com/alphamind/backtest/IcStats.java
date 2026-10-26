package com.alphamind.backtest;

/** 横截面秩相关：{@code dates} 为可计算 IC 的交易日数，t 值只用每隔 h 日取一次的不重叠序列。 */
public record IcStats(int dates, Double meanIc, Double positiveShare, Double tNonOverlap) {}
