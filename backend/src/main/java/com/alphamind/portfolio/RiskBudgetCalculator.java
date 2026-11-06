package com.alphamind.portfolio;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gate 5 数值的纯计算。拟买入金额从现金/低波动资产中扣除，总资产不变，因此交易后所有权重共用同一分母。
 * 任一持仓缺当期价格时不出百分比，避免旧价格或部分持仓算出看似精确的权重。
 */
public final class RiskBudgetCalculator {
    private RiskBudgetCalculator() {}

    /** 一只持仓的行情：有价格时 problem 为 null。 */
    public record Quote(Double price, String source, String problem) {
        public static Quote of(double price, String source) { return new Quote(price, source, null); }
        public static Quote failed(String problem) { return new Quote(null, null, problem); }
    }

    public static RiskBudget compute(PortfolioProfile profile, String stockCode, String theme, double plannedBuy,
                                     Map<String, Quote> quotes, OffsetDateTime now) {
        List<String> problems = new ArrayList<>();
        List<RiskBudget.Valuation> valuations = new ArrayList<>();
        double holdingsValue = 0, positionValue = 0, themeValue = 0;
        for (Holding holding : profile.holdings()) {
            double value;
            Double price = null;
            String source;
            if (holding.pricedByMarket()) {
                Quote quote = quotes.get(holding.stockCode());
                if (quote == null || quote.problem() != null || quote.price() == null) {
                    problems.add(holding.name() + "（" + holding.stockCode() + "）："
                            + (quote == null || quote.problem() == null ? "行情不可用" : quote.problem()));
                    continue;
                }
                price = quote.price();
                value = holding.shares() * price;
                source = quote.source();
            } else {
                value = holding.manualValue();
                source = "手工市值";
            }
            valuations.add(new RiskBudget.Valuation(holding.stockCode(), holding.name(), holding.theme(),
                    holding.shares(), price, round(value), source));
            holdingsValue += value;
            if (Objects.equals(holding.stockCode(), stockCode)) positionValue += value;
            if (Objects.equals(holding.theme(), theme)) themeValue += value;
        }
        double liquid = profile.cash() + profile.lowVolatility();
        double total = holdingsValue + liquid;
        if (plannedBuy < 0 || !Double.isFinite(plannedBuy)) problems.add("拟买入金额无效");
        else if (plannedBuy > liquid) problems.add("拟买入金额超过现金与低波动资产合计");
        if (total <= 0) problems.add("组合总资产为 0，请先录入持仓或现金");

        if (!problems.isEmpty()) {
            return new RiskBudget(stockCode, theme, plannedBuy, false, null, null, null, null, null,
                    profile.peakValue(), List.copyOf(valuations), List.copyOf(problems), now);
        }
        Double peak = profile.peakValue() == null ? null : Math.max(profile.peakValue(), total);
        Double drawdown = peak == null || peak <= 0 ? null : pct(peak - total, peak);
        if (drawdown == null) problems.add("未填写组合历史高点，回撤需手动填写");
        return new RiskBudget(stockCode, theme, plannedBuy, problems.isEmpty(), round(total),
                pct(positionValue + plannedBuy, total), pct(themeValue + plannedBuy, total),
                pct(liquid - plannedBuy, total), drawdown, peak == null ? null : round(peak),
                List.copyOf(valuations), List.copyOf(problems), now);
    }

    private static double pct(double part, double whole) {
        return Math.round(part / whole * 10000.0) / 100.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
