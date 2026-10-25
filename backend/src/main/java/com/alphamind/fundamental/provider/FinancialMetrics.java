package com.alphamind.fundamental.provider;

import java.time.LocalDate;

/**
 * 最新一期主要财务指标（报告期累计口径）。数值原样取自数据源，缺失即 null；
 * 比率字段单位为 %，金额单位为元。银行等金融企业的毛利率通常为 null。
 */
public record FinancialMetrics(
        String reportName,
        LocalDate reportDate,
        LocalDate noticeDate,
        String orgType,
        Double eps,
        Double deductedEps,
        Double ocfPerShare,
        Double revenue,
        Double parentNetProfit,
        Double deductedNetProfit,
        Double revenueYoy,
        Double parentNetProfitYoy,
        Double deductedNetProfitYoy,
        Double roe,
        Double grossMargin,
        Double debtRatio) {}
