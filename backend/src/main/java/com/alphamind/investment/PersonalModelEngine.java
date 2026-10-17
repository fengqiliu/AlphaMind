package com.alphamind.investment;

import com.alphamind.model.dto.MarketDataDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

import static com.alphamind.investment.PersonalModelReview.Decision.*;
import static com.alphamind.investment.PersonalModelReview.Valuation.*;

/** 版本化、确定性决策门。缺证据不升级；模型从不因跌价或超卖自动买入。 */
@Component
public class PersonalModelEngine {
    public PersonalModelAssessment evaluate(PersonalModelDefinition model, PersonalModelDefinition.Asset asset,
            MarketDataDTO market, PersonalModelReview input, LocalDate today) {
        PersonalModelReview review = input == null ? new PersonalModelReview() : input;
        Set<String> missing = new LinkedHashSet<>(), blocks = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();
        List<PersonalModelAssessment.Gate> gates = new ArrayList<>();
        Double price = market == null ? null : market.getCurrentPrice();
        int tier = 0;
        String zone = "UNKNOWN";
        if (price == null || !Double.isFinite(price) || price <= 0) missing.add("可核实的最新价格");
        else {
            for (var band : asset.bands()) if (price >= band.low() && price <= band.high()) tier = Math.max(tier, band.tier());
            zone = tier > 0 ? "TIER_" + tier : price > asset.bands().get(0).high() ? "ABOVE_FIRST"
                    : price < asset.bands().get(2).low() ? "BELOW_THIRD" : "BETWEEN_BANDS";
        }
        gates.add(gate("PRICE", "Gate 0 价格", tier > 0 ? "PASS" : "PENDING",
                tier > 0 ? "进入第 " + tier + " 档，仅触发复核" : "ABOVE_FIRST".equals(zone) ? "尚未进入首档，不追价"
                        : "价格缺失、位于档间或跌破第三档：仅复核，不自动升档"));

        boolean dates = review.getReviewedAt() != null && review.getValidUntil() != null
                && !review.getReviewedAt().isAfter(today) && !review.getValidUntil().isBefore(today)
                && !review.getValidUntil().isBefore(review.getReviewedAt());
        boolean sourced = review.getEvidenceSource() != null && !review.getEvidenceSource().isBlank();
        boolean trusted = dates && sourced;
        if (!dates) missing.add("复核日期及未过期的证据有效期");
        if (!sourced) missing.add("财报、公告或估值数据的来源与报告期");
        boolean valuationFail = review.getValuation() == OVERVALUED;
        boolean valuationPass = trusted && (review.getValuation() == REASONABLE || review.getValuation() == UNDERVALUED);
        if (valuationFail) blocks.add("估值未通过");
        else if (!valuationPass) missing.add("估值进入合理/低位的人工确认");
        gates.add(gate("VALUATION", "Gate 1 估值", status(valuationPass, valuationFail),
                asset.valuationFocus() + "；当前只接受注明来源的手工复核，行情 PE/PB 缺失不作通过"));

        int blockBefore = blocks.size();
        boolean quality = checked(review.getEarnings(), "盈利与扣非利润稳定", missing, blocks)
                & checked(review.getCashFlow(), "经营现金流支持盈利", missing, blocks);
        Map<String, PersonalModelReview.Decision> specifics = review.getSpecialChecks() == null ? Map.of() : review.getSpecialChecks();
        for (var check : asset.specialChecks().entrySet()) {
            if (!check.getKey().equals("goldExposure")) quality &= checked(specifics.get(check.getKey()), check.getValue(), missing, blocks);
        }
        gates.add(gate("QUALITY", "Gate 2 盈利与现金流", status(trusted && quality, blocks.size() > blockBefore), asset.fundamentalFocus()));

        blockBefore = blocks.size();
        boolean logic = checked(review.getBusinessLogic(), "核心业务逻辑仍成立", missing, blocks)
                & checked(review.getValueTrapClear(), "已排除价值陷阱与重大治理/财务风险", missing, blocks);
        if (review.getCounterArgument() == null || review.getCounterArgument().isBlank()) {
            missing.add("为什么现在不应该买的反方证据"); logic = false;
        }
        gates.add(gate("LOGIC", "Gate 3 核心逻辑", status(trusted && logic, blocks.size() > blockBefore), asset.vetoRisk()));

        var timing = PersonalTimingCalculator.calculate(market, today);
        boolean timingCurrent = false;
        try { timingCurrent = trusted && timing.dataAsOf() != null && !review.getReviewedAt().isBefore(LocalDate.parse(timing.dataAsOf())); }
        catch (RuntimeException ignored) { /* 无最新交易日，不确认当期技术形态。 */ }
        boolean weakRsi = timing.dailyRsi14() != null && timing.dailyRsi14() >= 25 && timing.dailyRsi14() <= 45;
        boolean calm = timing.atr20() != null && timing.atr20() > 0 && timing.trueRange() != null && timing.trueRange() <= 2 * timing.atr20();
        boolean freshMarket = market != null && Boolean.FALSE.equals(market.getStale())
                && ("LIVE".equals(market.getDataStatus()) || "FRESH_CACHE".equals(market.getDataStatus()));
        boolean timingPass = weakRsi && calm && timingCurrent && review.getWeeklyTiming() == PASS && freshMarket;
        if (timing.dailyRsi14() == null || timing.atr20() == null) missing.add("足够的真实日 K 线以计算 RSI14 与 ATR20");
        if (review.getWeeklyTiming() != PASS || !timingCurrent) missing.add("与最新交易日对应的日线/周线联合复核");
        if (!freshMarket) missing.add("未降级为过期缓存的行情");
        if (timing.weeklyRsi14() == null) notes.add("周线长度不足时周 RSI 留空；周线结论必须由当期人工证据确认。");
        if (timing.dailyRsi14() != null && timing.dailyRsi14() < 25) notes.add("RSI14 极端超卖：先排查业绩、信用与行业风险，暂缓升级。");
        if (timing.dailyRsi14() != null && timing.dailyRsi14() > 70) notes.add("RSI14 过热：不追价，复核估值与减速条件。");
        if (!calm && timing.atr20() != null) notes.add("当日真实波幅超过 2×ATR20 或 ATR 不可用，等待信息确认。");
        gates.add(gate("TIMING", "Gate 4 RSI / ATR 择时", timingPass ? "PASS" : "PENDING", "RSI14 25–45 区间、当期周线人工确认、波幅不超过 2×ATR20；技术不证明价值"));

        blockBefore = blocks.size();
        boolean risk = checked(review.getNoLeverage(), "默认不使用杠杆", missing, blocks);
        double limit = asset.category().equals("成长科技") ? 3 : 5;
        if (asset.category().equals("黄金矿股ETF")) {
            risk &= checked(specifics.get("goldExposure"), "已合并全部黄金相关敞口并确认可承受", missing, blocks);
            if (review.getGoldPositionLimitPct() == null) { missing.add("黄金矿股 ETF 的人工仓位上限"); risk = false; }
            else limit = review.getGoldPositionLimitPct();
        }
        risk &= ceiling(review.getProjectedPositionPct(), limit, "交易后单标的权重", missing, blocks);
        risk &= ceiling(review.getProjectedThemePct(), 20, "合并产业链/主题权重", missing, blocks);
        risk &= ceiling(review.getPortfolioDrawdownPct(), 20, "当前组合回撤", missing, blocks);
        if (review.getCashAfterTradePct() == null) { missing.add("交易后现金/低波动比例"); risk = false; }
        else if (!Double.isFinite(review.getCashAfterTradePct()) || review.getCashAfterTradePct() < 15 || review.getCashAfterTradePct() > 100) {
            blocks.add("交易后现金/低波动比例低于 15% 或数据无效"); risk = false;
        }
        if (review.getProjectedPositionPct() != null && review.getProjectedThemePct() != null
                && review.getProjectedThemePct() < review.getProjectedPositionPct()) { blocks.add("主题总敞口不能小于其中单标的权重"); risk = false; }
        if (review.getProjectedPositionPct() != null && review.getCashAfterTradePct() != null
                && review.getProjectedPositionPct() + review.getCashAfterTradePct() > 100) { blocks.add("仓位与现金比例合计超过 100%"); risk = false; }
        gates.add(gate("RISK", "组合风险预算", status(trusted && risk, blocks.size() > blockBefore),
                "本次起步单标的上限 " + limit + "%；主题取原文 20%–25% 的保守端 20%，现金底线 15%，组合回撤 20%"));

        boolean fundamental = valuationPass && trusted && quality && logic;
        String level = "ABOVE_FIRST".equals(zone) ? "S0" : "S1";
        if (tier > 0 && fundamental && blocks.isEmpty()) level = "S2";
        if (tier >= 2 && fundamental && risk && timingPass && blocks.isEmpty()) {
            level = "S3";
            if (review.getValuation() == UNDERVALUED && (review.getBottomDivergence() == PASS || review.getVolatilityContraction() == PASS)) level = "S4";
        }
        boolean candidate = level.equals("S3") || level.equals("S4");
        String action = candidate ? level.equals("S4") ? "重点加仓候选，仍受仓位上限约束" : "小幅建仓/加仓候选，分批复核执行"
                : !blocks.isEmpty() ? "暂缓；一票否决或组合约束未通过" : level.equals("S0") ? "不操作，继续监控" : level.equals("S2") ? "观察或极小仓复核，不升级强买入" : "仅触发复核，不自动买入";
        if (review.getBusinessLogic() == FAIL) action = "核心逻辑受损：暂缓并复核退出条件";
        if (tier > 0 && tier < 3 && timing.atr20() != null && price != null) {
            var next = asset.bands().get(tier);
            double ceiling = Math.min(next.high(), price - 1.5 * timing.atr20());
            timing = new PersonalModelAssessment.Timing(timing.dailyRsi14(), timing.weeklyRsi14(), timing.atr20(), timing.atrPricePct(), timing.trueRange(), ceiling > 0 ? PersonalTimingCalculator.round(ceiling) : null, timing.dataAsOf());
        }
        notes.add("价格区间来自 " + model.versionDate() + " 的 v" + model.version() + "，应随财报复核，不能视为当前合理价值。");
        notes.add("试仓 10%–20%、主建仓/强化各 25%–35%、机动 20%–30% 是单标的目标仓位内部示例，不是总资产比例。");
        notes.add("风险数值为人工输入的交易后合并敞口；不使用文档旧持仓推测当前账户，不生成固定止损价或收益目标。");
        if (asset.provisional()) notes.add("博通集成三档为暂定，额外确认扣非、现金流、库存、产品收入与估值回落后才可升级。");
        return new PersonalModelAssessment(model.id(), model.version(), model.versionDate(), model.sourceSha256(), asset,
                level, action, candidate, zone, price, gates, timing, List.copyOf(missing), List.copyOf(blocks), notes, review, today);
    }
    private static PersonalModelAssessment.Gate gate(String id, String label, String status, String detail) { return new PersonalModelAssessment.Gate(id,label,status,detail); }
    private static String status(boolean pass, boolean fail) { return fail ? "FAIL" : pass ? "PASS" : "PENDING"; }
    private static boolean checked(PersonalModelReview.Decision decision, String label, Set<String> missing, Set<String> blocks) {
        if (decision == FAIL) { blocks.add(label + "未通过"); return false; }
        if (decision != PASS) { missing.add(label); return false; }
        return true;
    }
    private static boolean ceiling(Double value, double limit, String label, Set<String> missing, Set<String> blocks) {
        if (value == null) { missing.add(label); return false; }
        if (!Double.isFinite(value) || value < 0 || value > limit) { blocks.add(label + "超过控制线 " + limit + "% 或数据无效"); return false; }
        return true;
    }
}
