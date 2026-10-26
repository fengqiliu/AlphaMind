package com.alphamind.backtest;

import java.time.LocalDate;

/** 一次回放样本：t 日收盘后的技术分，与 t+1 开盘买入、t+h 收盘卖出的收益。 */
public record BacktestSample(String code, LocalDate signalDate, int score, int horizon,
                             double grossReturn, double netReturn) {}
