package com.alphamind.ledger;

/**
 * 一个分组在某个持有期的统计。胜率只用有方向的信号：BUY 跑赢基准、SELL 跑输基准算对；HOLD 不计入。
 * 缺少基准数据的样本不参与超额与胜率。
 */
public record PerformanceRow(String dimension, String key, int horizonDays, int count,
                             Double meanNetReturn, Double meanExcessReturn, Double hitRate,
                             int directionalCount, boolean insufficient) {}
