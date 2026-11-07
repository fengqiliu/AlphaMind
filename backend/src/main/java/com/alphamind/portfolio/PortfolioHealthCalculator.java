package com.alphamind.portfolio;

import com.alphamind.investment.PersonalModelDefinition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 持仓体检的纯计算。阈值全部取自个人模型已有参数：单标的控制线（成长科技 3%、其余 5%）、主题 20%、
 * 现金底线 15%、组合回撤 20%、RSI14 > 70 过热；不引入模型之外的卖出规则。
 *
 * <p>任一按市价估值的持仓缺当期行情时，不做权重/现金/回撤检查（总资产不可信），但该持仓之外的
 * 过热与证据检查照常进行。
 */
public final class PortfolioHealthCalculator {
    static final double THEME_LIMIT = 20, CASH_FLOOR = 15, DRAWDOWN_LIMIT = 20, OVERHEATED_RSI = 70;
    static final String GOLD_ETF = "黄金矿股ETF";

    private PortfolioHealthCalculator() {}

    /** 一只持仓的当期行情与日 RSI14；problem 非空表示无法取得当期价格。 */
    public record HoldingQuote(Double price, Double rsi14, String problem) {
        public static HoldingQuote of(double price, Double rsi14) { return new HoldingQuote(price, rsi14, null); }
        public static HoldingQuote failed(String problem) { return new HoldingQuote(null, null, problem); }
    }

    /** 最近一次有来源复核的证据有效期；从未复核时不在映射中。 */
    public record ReviewStatus(LocalDate validUntil) {}

    static double positionLimit(PersonalModelDefinition.Asset asset) {
        return asset != null && asset.category().equals("成长科技") ? 3 : 5;
    }

    public static PortfolioHealth evaluate(PortfolioProfile profile, List<PersonalModelDefinition.Asset> assets,
                                           Map<String, HoldingQuote> quotes, Map<String, ReviewStatus> reviews,
                                           LocalDate today, OffsetDateTime now) {
        Map<String, PersonalModelDefinition.Asset> model = new HashMap<>();
        Set<String> modelThemes = new LinkedHashSet<>();
        for (var asset : assets) { model.put(asset.code(), asset); modelThemes.add(asset.theme()); }

        List<PortfolioHealth.Finding> findings = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        List<Map.Entry<Holding, Double>> values = new ArrayList<>();
        boolean complete = true;

        for (Holding holding : profile.holdings()) {
            var asset = holding.stockCode() == null ? null : model.get(holding.stockCode());
            if (holding.pricedByMarket()) {
                var quote = quotes.get(holding.stockCode());
                if (quote == null || quote.problem() != null || quote.price() == null) {
                    unavailable.add(holding.name() + "（" + holding.stockCode() + "）："
                            + (quote == null || quote.problem() == null ? "行情不可用" : quote.problem()));
                    complete = false;
                } else {
                    values.add(Map.entry(holding, holding.shares() * quote.price()));
                    if (asset != null && quote.rsi14() != null && quote.rsi14() > OVERHEATED_RSI) {
                        findings.add(new PortfolioHealth.Finding(PortfolioHealth.Kind.OVERHEATED, holding.stockCode(), holding.name(),
                                "日 RSI14 " + quote.rsi14() + " 过热：不追价，复核估值与减速条件", quote.rsi14(), OVERHEATED_RSI));
                    }
                }
            } else {
                values.add(Map.entry(holding, holding.manualValue()));
            }
            if (asset != null) reviewFinding(holding, reviews.get(holding.stockCode()), today).ifPresent(findings::add);
        }

        if (!complete) return new PortfolioHealth(null, List.copyOf(findings), List.copyOf(unavailable), now);

        double liquid = profile.cash() + profile.lowVolatility();
        double total = values.stream().mapToDouble(Map.Entry::getValue).sum() + liquid;
        if (total <= 0) return new PortfolioHealth(0.0, List.copyOf(findings), List.copyOf(unavailable), now);

        Map<String, Double> themes = new LinkedHashMap<>();
        for (var entry : values) {
            Holding holding = entry.getKey();
            double value = entry.getValue();
            if (modelThemes.contains(holding.theme())) themes.merge(holding.theme(), value, Double::sum);
            var asset = holding.stockCode() == null ? null : model.get(holding.stockCode());
            if (!holding.pricedByMarket() || (asset != null && asset.category().equals(GOLD_ETF))) continue;
            double pct = pct(value, total), limit = positionLimit(asset);
            if (pct > limit) findings.add(new PortfolioHealth.Finding(PortfolioHealth.Kind.POSITION_LIMIT, holding.stockCode(),
                    holding.name(), "单标的权重 " + pct + "% 超过控制线 " + limit + "%：复核是否需要再平衡", pct, limit));
        }
        themes.forEach((theme, value) -> {
            double pct = pct(value, total);
            if (pct > THEME_LIMIT) findings.add(new PortfolioHealth.Finding(PortfolioHealth.Kind.THEME_LIMIT, null, theme,
                    theme + "合计 " + pct + "% 超过 " + THEME_LIMIT + "%：合并敞口偏高", pct, THEME_LIMIT));
        });
        double cashPct = pct(liquid, total);
        if (cashPct < CASH_FLOOR) findings.add(new PortfolioHealth.Finding(PortfolioHealth.Kind.CASH_FLOOR, null, "现金及低波动",
                "现金及低波动比例 " + cashPct + "% 低于底线 " + CASH_FLOOR + "%", cashPct, CASH_FLOOR));
        if (profile.peakValue() != null && profile.peakValue() > 0) {
            double peak = Math.max(profile.peakValue(), total), drawdown = pct(peak - total, peak);
            if (drawdown > DRAWDOWN_LIMIT) findings.add(new PortfolioHealth.Finding(PortfolioHealth.Kind.DRAWDOWN, null, "组合",
                    "组合回撤 " + drawdown + "% 超过 " + DRAWDOWN_LIMIT + "%：复核风险预算", drawdown, DRAWDOWN_LIMIT));
        }
        return new PortfolioHealth(Math.round(total * 100) / 100.0, List.copyOf(findings), List.copyOf(unavailable), now);
    }

    private static Optional<PortfolioHealth.Finding> reviewFinding(Holding holding, ReviewStatus review, LocalDate today) {
        if (review == null) return Optional.of(new PortfolioHealth.Finding(PortfolioHealth.Kind.REVIEW_MISSING, holding.stockCode(),
                holding.name(), "还没有有来源的复核：持有逻辑需要确认", null, null));
        if (review.validUntil() == null || review.validUntil().isBefore(today))
            return Optional.of(new PortfolioHealth.Finding(PortfolioHealth.Kind.REVIEW_EXPIRED, holding.stockCode(), holding.name(),
                    "最近一次复核的证据" + (review.validUntil() == null ? "未写有效期" : "已于 " + review.validUntil() + " 过期")
                            + "：持有逻辑需要重新确认", null, null));
        return Optional.empty();
    }

    private static double pct(double part, double whole) {
        return Math.round(part / whole * 10000.0) / 100.0;
    }
}
