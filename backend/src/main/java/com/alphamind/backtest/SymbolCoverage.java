package com.alphamind.backtest;

import java.time.LocalDate;

/** 参与回测的单只股票数据区间。 */
public record SymbolCoverage(String code, String name, LocalDate from, LocalDate to, int bars) {}
