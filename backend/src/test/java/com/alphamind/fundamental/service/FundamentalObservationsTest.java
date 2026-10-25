package com.alphamind.fundamental.service;

import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalObservationsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private static FinancialMetrics financials(String orgType, Double eps, Double ocf, Double deductedYoy, LocalDate reportDate) {
        return new FinancialMetrics("2026中报", reportDate, LocalDate.of(2026, 8, 25), orgType,
                eps, eps, ocf, 1e11, 6e9, 5.9e9, 20.0, 7.0, deductedYoy, 8.76, 11.8, 67.4);
    }

    @Test
    void describesPercentileNeutrallyAndFlagsNegativeOperatingCashFlow() {
        var valuation = new ValuationMetrics(LocalDate.of(2026, 10, 23), 30.0, 2e11, 21.94, 4.19, null, null, null,
                35.2, 22.1, 1250, 1250, LocalDate.of(2021, 10, 25));

        List<String> notes = FundamentalObservations.describe(valuation,
                financials("通用", 1.08, -0.33, -3.5, LocalDate.of(2026, 6, 30)), TODAY);

        assertTrue(notes.stream().anyMatch(n -> n.contains("PE_TTM 21.94") && n.contains("35.20% 分位")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("每股经营现金流 -0.33 元为负")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("扣非净利润同比 -3.50%（下降）")));
        assertTrue(notes.stream().noneMatch(n -> n.contains("低估") || n.contains("通过")), "只描述数据，不下结论");
    }

    @Test
    void lossMakingPeAndStaleDataAreDisclosed() {
        var valuation = new ValuationMetrics(LocalDate.of(2026, 10, 1), 10.0, 5e9, -12.0, 2.0, null, null, null,
                null, null, 0, 300, LocalDate.of(2025, 8, 1));

        List<String> notes = FundamentalObservations.describe(valuation,
                financials("通用", 0.1, 0.2, 1.0, LocalDate.of(2025, 12, 31)), TODAY);

        assertTrue(notes.stream().anyMatch(n -> n.startsWith("PE_TTM 为负或缺失")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("不足以计算分位")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("不是最近交易日")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("超过 200 天")));
    }

    @Test
    void bankUsesBankSpecificHintInsteadOfCashFlowRatio() {
        List<String> notes = FundamentalObservations.describe(null,
                financials("银行", 2.98, -5.0, 2.0, LocalDate.of(2026, 6, 30)), TODAY);

        assertTrue(notes.stream().anyMatch(n -> n.startsWith("未取得估值数据")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("PB–ROE")));
        assertTrue(notes.stream().noneMatch(n -> n.contains("每股经营现金流")));
    }

    @Test
    void evidenceSummaryCarriesSourceAndReportPeriod() {
        var valuation = new ValuationMetrics(LocalDate.of(2026, 10, 23), 30.0, 2e11, 21.94, 4.19, null, null, null,
                35.2, null, 1250, 1250, LocalDate.of(2021, 10, 25));

        String summary = FundamentalObservations.evidenceSummary(valuation,
                financials("通用", 1.08, -0.33, 6.47, LocalDate.of(2026, 6, 30)), TODAY);

        assertTrue(summary.startsWith("东方财富数据中心（自动取数 2026-10-25，未经第三方核验）"));
        assertTrue(summary.contains("PE_TTM 21.94（近 5 年 35.20% 分位）"));
        assertTrue(summary.contains("PB_MRQ 4.19；"), "PB 分位缺失时不补写");
        assertTrue(summary.contains("2026中报（公告 2026-08-25）"));
    }
}
