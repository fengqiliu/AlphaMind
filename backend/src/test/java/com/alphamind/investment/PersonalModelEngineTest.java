package com.alphamind.investment;

import com.alphamind.model.dto.*;
import com.alphamind.service.AnalysisReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static com.alphamind.investment.PersonalModelReview.Decision.*;
import static com.alphamind.investment.PersonalModelReview.Valuation.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalModelEngineTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PersonalModelCatalog catalog = new PersonalModelCatalog(mapper);
    private final PersonalModelEngine engine = new PersonalModelEngine();
    private final LocalDate today = LocalDate.of(2026, 10, 16);
    PersonalModelEngineTest() throws Exception {}

    @Test void importsAll13SourceBandsAndBotongProvisionalRules() {
        assertEquals(13, catalog.definition().assets().size());
        assertEquals("1.1", catalog.definition().version());
        assertEquals(31, catalog.asset("603068").bands().get(1).low());
        assertEquals(34, catalog.asset("603068").bands().get(1).high());
        assertTrue(catalog.asset("603068").provisional());
        assertEquals(5, catalog.asset("603068").specialChecks().size());
        assertEquals(1.68, catalog.asset("517400").bands().get(0).high());
        assertEquals(33.5, catalog.asset("002352").bands().get(0).high());
        assertThrows(IllegalArgumentException.class, () -> catalog.asset("600519"));
    }
    @Test void abovePriceIsS0EvenWhenOtherEvidenceIsPositive() {
        assertEquals("S0", assess("002352", 40, confirmed(), market(40)).level());
    }
    @Test void priceTouchWithoutFinancialEvidenceNeverBecomesBuy() {
        var result = assess("002352", 29.5, null, market(29.5));
        assertEquals("S1", result.level()); assertFalse(result.entryCandidate());
        assertTrue(result.missingEvidence().stream().anyMatch(s -> s.contains("估值")));
    }
    @Test void gapAndBelowThirdDoNotAutoPromote() {
        for (double price : new double[]{28.2, 24}) {
            var result = assess("002352", price, confirmed(), market(price));
            assertEquals("S1", result.level()); assertFalse(result.entryCandidate());
        }
    }
    @Test void firstTierIsNotStrongEntryEvenWithGoodEvidence() {
        assertEquals("S2", assess("002352", 32, confirmed(), market(32)).level());
    }
    @Test void secondTierWeakRsiAndAllGatesBecomeS3() {
        var result = assess("002352", 29.5, confirmed(), market(29.5));
        assertEquals("S3", result.level()); assertTrue(result.entryCandidate());
        assertTrue(result.timing().dailyRsi14() >= 25 && result.timing().dailyRsi14() <= 45);
        assertNotNull(result.timing().weeklyRsi14());
    }
    @Test void s4RequiresUndervaluedAndCurrentPatternConfirmation() {
        var review = confirmed(); review.setValuation(UNDERVALUED); review.setBottomDivergence(PASS);
        assertEquals("S4", assess("002352", 29.5, review, market(29.5)).level());
        review.setValuation(REASONABLE);
        assertEquals("S3", assess("002352", 29.5, review, market(29.5)).level());
    }
    @Test void fundamentalVetoBeatsOversoldOrLowPrice() {
        var review = confirmed(); review.setEarnings(FAIL); review.setCashFlow(FAIL);
        var result = assess("002352", 29.5, review, market(29.5));
        assertEquals("S1", result.level()); assertFalse(result.entryCandidate()); assertFalse(result.blockers().isEmpty());
    }
    @Test void brokenLogicTriggersExitReviewWithoutAssumingExistingHolding() {
        var review = confirmed(); review.setBusinessLogic(FAIL);
        assertTrue(assess("002352", 29.5, review, market(29.5)).action().contains("复核退出"));
    }
    @Test void expiredOrUnsourcedEvidenceCannotConfirmValue() {
        var review = confirmed(); review.setValidUntil(today.minusDays(1));
        assertEquals("S1", assess("002352", 29.5, review, market(29.5)).level());
        review = confirmed(); review.setEvidenceSource(" ");
        assertEquals("S1", assess("002352", 29.5, review, market(29.5)).level());
    }
    @Test void financialReviewFromBeforeLatestCandleCannotConfirmTechnicalPatterns() {
        var review = confirmed(); review.setReviewedAt(today.minusDays(1)); review.setBottomDivergence(PASS);
        assertEquals("S2", assess("002352", 29.5, review, market(29.5)).level());
    }
    @Test void staleMarketCannotBecomeEntryCandidate() {
        var market = market(29.5); market.setStale(true); market.setDataStatus("STALE_CACHE");
        assertFalse(assess("002352", 29.5, confirmed(), market).entryCandidate());
    }
    @Test void excessiveSingleThemeCashAndDrawdownEachBlockEntry() {
        for (int dimension = 0; dimension < 4; dimension++) {
            var review = confirmed();
            if (dimension == 0) review.setProjectedPositionPct(5.01);
            if (dimension == 1) review.setProjectedThemePct(20.01);
            if (dimension == 2) review.setCashAfterTradePct(14.99);
            if (dimension == 3) review.setPortfolioDrawdownPct(20.01);
            assertFalse(assess("002352", 29.5, review, market(29.5)).entryCandidate());
        }
    }
    @Test void missingPortfolioDoesNotInventCurrentHoldings() {
        var review = confirmed(); review.setProjectedPositionPct(null);
        var result = assess("002352", 29.5, review, market(29.5));
        assertEquals("S2", result.level()); assertFalse(result.entryCandidate());
    }
    @Test void botongRequiresAllSpecialEvidenceAndGrowthStartingLimit() {
        var review = confirmed(); review.setProjectedPositionPct(2.0);
        assertEquals("S1", assess("603068", 32, review, market(32)).level());
        var checks = new LinkedHashMap<String, PersonalModelReview.Decision>();
        catalog.asset("603068").specialChecks().keySet().forEach(key -> checks.put(key, PASS));
        review.setSpecialChecks(checks);
        assertEquals("S3", assess("603068", 32, review, market(32)).level());
        review.setProjectedPositionPct(3.1);
        assertFalse(assess("603068", 32, review, market(32)).entryCandidate());
    }
    @Test void goldRequiresMergedExposureAndExplicitBudget() {
        var review = confirmed(); review.setSpecialChecks(Map.of("goldExposure",PASS));
        assertFalse(assess("517400",1.5,review,market(1.5)).entryCandidate());
        review.setGoldPositionLimitPct(6.0);
        assertTrue(assess("517400",1.5,review,market(1.5)).entryCandidate());
    }
    @Test void assessmentAndNullableForecastRoundTripInReportMapper() {
        var result = assess("002352", 29.5, confirmed(), market(29.5));
        var report = AnalysisReportDTO.builder().id("personal").stockCode("002352").stockName("顺丰控股")
                .personalModel(result).tradeSignal(TradeSignalDTO.builder().entryPrice(29.5).type(com.alphamind.model.enums.SignalType.BUY).rationale(result.action()).build())
                .finalSignal(com.alphamind.model.enums.SignalType.BUY).build();
        var reportMapper = new AnalysisReportMapper(mapper);
        var restored = reportMapper.toDTO(reportMapper.toEntity(report));
        assertEquals(result, restored.getPersonalModel());
        assertNull(restored.getTradeSignal().getTargetPrice()); assertNull(restored.getTradeSignal().getStopLoss());
        assertNull(restored.getConfidence());
    }
    private PersonalModelAssessment assess(String code, double price, PersonalModelReview review, MarketDataDTO market) {
        return engine.evaluate(catalog.definition(),catalog.asset(code),market,review,today);
    }
    private PersonalModelReview confirmed() {
        var r = new PersonalModelReview(); r.setReviewedAt(today); r.setValidUntil(today.plusDays(30));
        r.setEvidenceSource("测试用财报与估值证据，非真实研究结论"); r.setCounterArgument("测试反方证据"); r.setValuation(REASONABLE);
        r.setEarnings(PASS); r.setCashFlow(PASS); r.setBusinessLogic(PASS); r.setValueTrapClear(PASS); r.setWeeklyTiming(PASS); r.setNoLeverage(PASS);
        r.setProjectedPositionPct(3.0); r.setProjectedThemePct(10.0); r.setCashAfterTradePct(20.0); r.setPortfolioDrawdownPct(10.0); return r;
    }
    private MarketDataDTO market(double price) {
        List<double[]> bars = new ArrayList<>(); List<String> dates = new ArrayList<>();
        double close = price * 1.2;
        for (int i = 0; i < 180; i++) {
            close += (i % 2 == 0 ? 0.2 : -0.3) * price / 30;
            bars.add(new double[]{close, close, close - price / 30, close + price / 30});
            dates.add(today.minusDays(179 - i).toString());
        }
        return MarketDataDTO.builder().currentPrice(price).klines(bars).klineDates(dates).stale(false).dataStatus("LIVE").build();
    }
}
