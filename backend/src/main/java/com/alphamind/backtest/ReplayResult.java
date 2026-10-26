package com.alphamind.backtest;

import java.util.List;

/** 单只股票的回放结果；{@code limitUpSkipped} 为因次日一字涨停无法买入而剔除的信号日数。 */
public record ReplayResult(List<BacktestSample> samples, int limitUpSkipped) {}
