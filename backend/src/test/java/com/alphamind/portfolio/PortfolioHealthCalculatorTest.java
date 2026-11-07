package com.alphamind.portfolio;

import com.alphamind.investment.PersonalModelDefinition;
import com.alphamind.portfolio.PortfolioHealth.Kind;
import com.alphamind.portfolio.PortfolioHealthCalculator.HoldingQuote;
import com.alphamind.portfolio.PortfolioHealthCalculator.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioHealthCalculatorTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-06T10:00:00+08:00");
    static final String AI = "AI/电子链";

    static PersonalModelDefinition.Asset asset(String code, String name, String category, String theme) {
        return new PersonalModelDefinition.Asset(code, name, category, theme,
                List.of(new PersonalModelDefinition.Band(1, 1, 2)), "", "", "", Map.of(), false);
    }

    static final List<PersonalModelDefinition.Asset> ASSETS = List.of(
            asset("601138", "工业富联", "成长科技", AI),
            asset("002475", "立讯精密", "成长科技", AI),
            asset("600036", "招商银行", "银行", "银行"),
            asset("517400", "黄金股ETF国泰", "黄金矿股ETF", "黄金相关资产"));

    static final Map<String, ReviewStatus> FRESH = Map.of(
            "601138", new ReviewStatus(TODAY), "002475", new ReviewStatus(TODAY),
            "600036", new ReviewStatus(TODAY), "517400", new ReviewStatus(TODAY));

    static List<Kind> kinds(PortfolioHealth health) {
        return health.findings().stream().map(PortfolioHealth.Finding::kind).toList();
    }

    @Test
    void healthyPortfolioHasNoFindings() {
        // 总资产 100,000：招行 4%、工业富联 2%、立讯 1%（AI 合计 3%），现金 93%
        var profile = new PortfolioProfile(List.of(
                new Holding("600036", "招商银行", "银行", 100.0, null),
                new Holding("601138", "工业富联", AI, 100.0, null),
                new Holding("002475", "立讯精密", AI, 25.0, null)), 93_000.0, 0.0, 100_000.0, NOW);
        var quotes = Map.of("600036", HoldingQuote.of(40, 50.0), "601138", HoldingQuote.of(20, 60.0),
                "002475", HoldingQuote.of(40, 45.0));

        var health = PortfolioHealthCalculator.evaluate(profile, ASSETS, quotes, FRESH, TODAY, NOW);

        assertEquals(100_000.0, health.totalValue());
        assertEquals(List.of(), health.findings());
    }

    @Test
    void flagsEveryRiskBudgetBreach() {
        // 总资产 100,000：工业富联 40%（成长科技控制线 3%），立讯 20%，AI 合计 60%；招行 30%（控制线 5%）；现金 10%；高点 150,000 → 回撤 33.33%
        var profile = new PortfolioProfile(List.of(
                new Holding("601138", "工业富联", AI, 1000.0, null),
                new Holding("002475", "立讯精密", AI, 500.0, null),
                new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000.0, 0.0, 150_000.0, NOW);
        var quotes = Map.of("601138", HoldingQuote.of(40, 72.5), "002475", HoldingQuote.of(40, 50.0),
                "600036", HoldingQuote.of(30, 50.0));

        var health = PortfolioHealthCalculator.evaluate(profile, ASSETS, quotes, FRESH, TODAY, NOW);

        assertEquals(List.of(Kind.OVERHEATED, Kind.POSITION_LIMIT, Kind.POSITION_LIMIT, Kind.POSITION_LIMIT,
                Kind.THEME_LIMIT, Kind.THEME_LIMIT, Kind.CASH_FLOOR, Kind.DRAWDOWN), kinds(health));
        var theme = health.findings().stream().filter(f -> f.kind() == Kind.THEME_LIMIT && f.name().equals(AI)).findFirst().orElseThrow();
        assertEquals(60.0, theme.value());
        assertEquals(33.33, health.findings().get(health.findings().size() - 1).value());
        assertEquals(3.0, health.findings().get(1).limit(), "成长科技控制线 3%");
    }

    @Test
    void userThemesAndManualValuesAndGoldEtfAreNotCheckedForLimits() {
        var profile = new PortfolioProfile(List.of(
                new Holding(null, "某债基", "债券", null, 50_000.0),
                new Holding("517400", "黄金股ETF国泰", "黄金相关资产", 10_000.0, null)), 20_000.0, 10_000.0, null, NOW);
        var health = PortfolioHealthCalculator.evaluate(profile, ASSETS,
                Map.of("517400", HoldingQuote.of(1.5, 40.0)), FRESH, TODAY, NOW);

        assertEquals(List.of(), health.findings(), "债券是用户主题、手工市值不查单标的；黄金 ETF 上限由人工填写，不查单标的");
    }

    @Test
    void missingQuoteSkipsPortfolioChecksButKeepsPerHoldingOnes() {
        var profile = new PortfolioProfile(List.of(
                new Holding("601138", "工业富联", AI, 1000.0, null),
                new Holding("600036", "招商银行", "银行", 1000.0, null)), 0.0, 0.0, null, NOW);
        var health = PortfolioHealthCalculator.evaluate(profile, ASSETS,
                Map.of("601138", HoldingQuote.failed("实时行情不可用，只有过期缓存"), "600036", HoldingQuote.of(30, 75.0)),
                Map.of("600036", new ReviewStatus(TODAY.minusDays(1))), TODAY, NOW);

        assertNull(health.totalValue());
        assertEquals(List.of("工业富联（601138）：实时行情不可用，只有过期缓存"), health.unavailable());
        assertEquals(List.of(Kind.REVIEW_MISSING, Kind.OVERHEATED, Kind.REVIEW_EXPIRED), kinds(health));
    }

    @Test
    void nonModelStocksUseTheGeneralLimitButNoRsiOrReviewChecks() {
        var profile = new PortfolioProfile(List.of(new Holding("600519", "贵州茅台", "消费", 10.0, null)),
                90_000.0, 0.0, null, NOW);
        var health = PortfolioHealthCalculator.evaluate(profile, ASSETS,
                Map.of("600519", HoldingQuote.of(1000, 85.0)), Map.of(), TODAY, NOW);

        assertEquals(List.of(Kind.POSITION_LIMIT), kinds(health));
        assertEquals(5.0, health.findings().get(0).limit());
    }
}
