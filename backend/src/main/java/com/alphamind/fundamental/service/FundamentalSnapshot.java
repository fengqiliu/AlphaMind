package com.alphamind.fundamental.service;

import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;

import java.time.Instant;

/**
 * 一次成功抓取的财报与估值数据。估值或财报任一可为 null（数据源只返回其中一部分），
 * 但不会两者皆空。{@code retrievedAt} 用于缓存新鲜度与过期降级。
 */
public record FundamentalSnapshot(String stockCode, ValuationMetrics valuation, FinancialMetrics financials,
                                  String sourceName, Instant retrievedAt) {}
