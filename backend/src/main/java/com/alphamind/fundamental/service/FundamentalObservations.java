package com.alphamind.fundamental.service;

import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把自动取得的财报/估值转成中性的复核提示。只描述数据（分位、同比、正负），
 * 不给出“低估”“通过”等结论——Gate 1/2 的结论仍由人工确认。
 */
public final class FundamentalObservations {

    public static final String DISCLAIMER =
            "以上为东方财富数据中心自动取数，未经第三方核验；只作复核参考，估值与盈利结论仍需人工确认。";
    static final int STALE_VALUATION_DAYS = 7;
    static final int STALE_REPORT_DAYS = 200;

    private FundamentalObservations() {}

    public static List<String> describe(ValuationMetrics valuation, FinancialMetrics financials, LocalDate today) {
        List<String> notes = new ArrayList<>();
        if (valuation == null) notes.add("未取得估值数据，Gate 1 仍需人工提供估值依据。");
        else {
            multiple(notes, "PE_TTM", valuation.peTtm(), valuation.pePercentile(), valuation.peSampleDays(), valuation.historyFrom(),
                    "为负或缺失（可能亏损），PE 及其分位不适用于估值判断。");
            multiple(notes, "PB_MRQ", valuation.pbMrq(), valuation.pbPercentile(), valuation.pbSampleDays(), valuation.historyFrom(),
                    "为负或缺失，PB 及其分位不可用。");
            if (ChronoUnit.DAYS.between(valuation.tradeDate(), today) > STALE_VALUATION_DAYS)
                notes.add("估值数据截至 " + valuation.tradeDate() + "，不是最近交易日。");
        }
        if (financials == null) {
            notes.add("未取得财报数据，Gate 2 仍需人工提供财报依据。");
            return notes;
        }
        notes.add("最新报告期 " + label(financials) + "。");
        if (ChronoUnit.DAYS.between(financials.reportDate(), today) > STALE_REPORT_DAYS)
            notes.add("最新报告期距今超过 " + STALE_REPORT_DAYS + " 天，可能有更新财报尚未收录。");
        if (financials.deductedNetProfit() != null && financials.deductedNetProfit() < 0)
            notes.add("扣非净利润为负。");
        if (financials.deductedNetProfitYoy() != null)
            notes.add("扣非净利润同比 " + signed(financials.deductedNetProfitYoy()) + "%"
                    + (financials.deductedNetProfitYoy() < 0 ? "（下降）。" : "。"));
        if (isBank(financials)) {
            notes.add("银行口径：毛利率与经营现金流对盈利质量参考有限，请结合 PB–ROE、不良率、拨备与资本充足率复核。");
        } else if (financials.ocfPerShare() != null) {
            if (financials.ocfPerShare() < 0)
                notes.add("每股经营现金流 " + fmt(financials.ocfPerShare()) + " 元为负，经营现金流未覆盖利润，现金流项需重点核对。");
            else if (financials.eps() != null && financials.eps() > 0 && financials.ocfPerShare() / financials.eps() < 0.5)
                notes.add("每股经营现金流仅为基本每股收益的 " + fmt(financials.ocfPerShare() / financials.eps() * 100) + "%。");
        }
        return notes;
    }

    /** 可直接填入“证据来源与报告期”的一段摘要；只含取数事实，不含结论。 */
    public static String evidenceSummary(ValuationMetrics valuation, FinancialMetrics financials, LocalDate retrievedOn) {
        List<String> parts = new ArrayList<>();
        if (valuation != null) {
            parts.add("估值截至 " + valuation.tradeDate() + "：PE_TTM " + fmtOrDash(valuation.peTtm())
                    + percentileText(valuation.pePercentile()) + "，PB_MRQ " + fmtOrDash(valuation.pbMrq())
                    + percentileText(valuation.pbPercentile()));
        }
        if (financials != null) {
            StringBuilder text = new StringBuilder("财报 " + label(financials));
            if (financials.deductedNetProfitYoy() != null) text.append("，扣非净利润同比 ").append(signed(financials.deductedNetProfitYoy())).append('%');
            if (financials.ocfPerShare() != null) text.append("，每股经营现金流 ").append(fmt(financials.ocfPerShare())).append(" 元");
            if (financials.roe() != null) text.append("，ROE ").append(fmt(financials.roe())).append('%');
            parts.add(text.toString());
        }
        return "东方财富数据中心（自动取数 " + retrievedOn + "，未经第三方核验）：" + String.join("；", parts) + "。";
    }

    private static void multiple(List<String> notes, String name, Double value, Double percentile, int samples,
                                 LocalDate from, String invalidText) {
        if (value == null || value <= 0) notes.add(name + " " + invalidText);
        else if (percentile == null) notes.add(name + " " + fmt(value) + "；有效历史样本 " + samples + " 个交易日，不足以计算分位。");
        else notes.add(name + " " + fmt(value) + "，位于 " + from + " 以来 " + samples + " 个有效交易日的 " + fmt(percentile) + "% 分位。");
    }

    private static String label(FinancialMetrics financials) {
        String name = financials.reportName() != null ? financials.reportName() : financials.reportDate().toString();
        return financials.noticeDate() == null ? name : name + "（公告 " + financials.noticeDate() + "）";
    }

    private static boolean isBank(FinancialMetrics financials) {
        return financials.orgType() != null && financials.orgType().contains("银行");
    }

    private static String percentileText(Double percentile) {
        return percentile == null ? "" : "（近 5 年 " + fmt(percentile) + "% 分位）";
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "") + fmt(value);
    }

    private static String fmtOrDash(Double value) {
        return value == null ? "—" : fmt(value);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
