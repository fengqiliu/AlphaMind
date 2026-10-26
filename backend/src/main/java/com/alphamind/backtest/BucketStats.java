package com.alphamind.backtest;

/** 一个分数段的统计；样本为 0 时除 count 外均为 null。 */
public record BucketStats(String label, int count, Double meanNet, Double medianNet, Double winRate, Double meanExcess) {}
