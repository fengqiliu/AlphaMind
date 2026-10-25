package com.alphamind.fundamental.provider;

import java.time.LocalDate;

/**
 * 最新交易日估值指标及近 N 年历史分位。数值原样取自数据源，缺失即 null。
 *
 * <p>分位定义：历史样本中 ≤ 当前值的交易日占比（0–100）。只统计正值样本；
 * 当前值 ≤0（亏损或净资产为负）或有效样本不足 {@link EastmoneyFundamentalProvider#MIN_PERCENTILE_SAMPLES}
 * 时分位为 null，不做外推。
 */
public record ValuationMetrics(
        LocalDate tradeDate,
        Double closePrice,
        Double totalMarketCap,
        Double peTtm,
        Double pbMrq,
        Double pcfOcfTtm,
        Double psTtm,
        Double pegCar,
        Double pePercentile,
        Double pbPercentile,
        int peSampleDays,
        int pbSampleDays,
        LocalDate historyFrom) {}
