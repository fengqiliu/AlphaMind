package com.alphamind.investment;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.service.AnalysisHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.alphamind.investment.PersonalModelReview.Decision.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalReviewReuseServiceTest {
    private final PersonalModelCatalog catalog = new PersonalModelCatalog(new ObjectMapper());
    private final AnalysisHistoryService history = mock(AnalysisHistoryService.class);
    private final PersonalReviewReuseService service = new PersonalReviewReuseService(catalog, history);
    PersonalReviewReuseServiceTest() throws Exception {}

    @Test void financialEvidenceIsCopiedButCurrentDecisionsAndRiskAreReset() {
        var source = review();
        var report = report("603068",source,catalog.definition().sourceSha256());
        when(history.getLatestPersonalReview("603068")).thenReturn(Optional.of(report));
        var draft = service.latest("603068").orElseThrow();
        assertEquals("source-report",draft.sourceReportId());
        assertEquals("source-report",draft.review().getSourceReportId());
        assertEquals(source.getEvidenceSource(),draft.review().getEvidenceSource());
        assertEquals(source.getReviewedAt(),draft.review().getReviewedAt());
        assertEquals(source.getValidUntil(),draft.review().getValidUntil());
        assertEquals(PASS,draft.review().getEarnings());
        assertEquals(PASS,draft.review().getCashFlow());
        assertEquals(PersonalModelReview.Valuation.UNKNOWN,draft.review().getValuation());
        assertEquals(UNKNOWN,draft.review().getBusinessLogic());
        assertEquals(UNKNOWN,draft.review().getValueTrapClear());
        assertEquals(UNKNOWN,draft.review().getWeeklyTiming());
        assertEquals(UNKNOWN,draft.review().getBottomDivergence());
        assertEquals(UNKNOWN,draft.review().getVolatilityContraction());
        assertEquals(UNKNOWN,draft.review().getNoLeverage());
        assertNull(draft.review().getProjectedPositionPct());
        assertNull(draft.review().getProjectedThemePct());
        assertNull(draft.review().getCashAfterTradePct());
        assertNull(draft.review().getPortfolioDrawdownPct());
        assertNull(draft.review().getGoldPositionLimitPct());
        assertEquals(Map.of("cashRecovery",PASS),draft.review().getSpecialChecks());
        assertFalse(draft.expired());
        // 派生草稿不能清空源报告的状态、日期或 Map。
        draft.review().getSpecialChecks().clear();
        assertEquals(PASS,source.getWeeklyTiming());
        assertEquals(2.0,source.getProjectedPositionPct());
        assertEquals(3,source.getSpecialChecks().size());
        verify(history,never()).save(any());
    }
    @Test void expiredEvidenceKeepsOriginalDatesAndRemainsUnconfirmed() {
        var source=review(); source.setValidUntil(LocalDate.now().minusDays(1));
        when(history.getLatestPersonalReview("603068")).thenReturn(Optional.of(report("603068",source,catalog.definition().sourceSha256())));
        var draft=service.latest("603068").orElseThrow();
        assertTrue(draft.expired()); assertEquals(source.getValidUntil(),draft.review().getValidUntil());
        var assessment=new PersonalModelEngine().evaluate(catalog.definition(),catalog.asset("603068"),null,draft.review(),LocalDate.now());
        assertFalse(assessment.entryCandidate());
        assertEquals("PENDING",assessment.gates().stream().filter(g->g.id().equals("VALUATION")).findFirst().orElseThrow().status());
    }
    @Test void noRecordAndUnsupportedStockDoNotCreateHistory() {
        when(history.getLatestPersonalReview("002352")).thenReturn(Optional.empty());
        assertTrue(service.latest("002352").isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.latest("600519"));
        verify(history,never()).getLatestPersonalReview("600519"); verify(history,never()).save(any());
    }
    @Test void changedModelVersionOrSourceHashIsRejected() {
        when(history.getLatestPersonalReview("603068")).thenReturn(Optional.of(report("603068",review(),"different-source")));
        assertThrows(PersonalReviewReuseService.ModelVersionMismatch.class,()->service.latest("603068"));
    }
    @Test void provenanceCannotReferenceAnotherStockOrMissingReport() {
        var input=review(); input.setSourceReportId("source-report");
        when(history.getReport("source-report")).thenReturn(Optional.of(report("603068",review(),catalog.definition().sourceSha256())));
        assertThrows(IllegalArgumentException.class,()->service.validateSource("002352",input));
        when(history.getReport("source-report")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,()->service.validateSource("603068",input));
    }
    @Test void matchingProvenanceIsAcceptedWithoutWritingOrExtendingEvidence() {
        var input=review(); input.setSourceReportId("source-report");
        when(history.getReport("source-report")).thenReturn(Optional.of(report("603068",review(),catalog.definition().sourceSha256())));
        assertDoesNotThrow(()->service.validateSource("603068",input)); verify(history,never()).save(any());
    }
    private PersonalModelReview review() {
        var r=new PersonalModelReview();r.setReviewedAt(LocalDate.now().minusDays(2));r.setValidUntil(LocalDate.now().plusDays(30));
        r.setEvidenceSource("测试财报及估值依据，仅测试使用");r.setCounterArgument("测试反方理由");r.setEarnings(PASS);r.setCashFlow(PASS);
        r.setValuation(PersonalModelReview.Valuation.UNDERVALUED);r.setBusinessLogic(PASS);r.setValueTrapClear(PASS);
        r.setWeeklyTiming(PASS);r.setBottomDivergence(PASS);r.setVolatilityContraction(PASS);r.setNoLeverage(PASS);
        r.setProjectedPositionPct(2.0);r.setProjectedThemePct(10.0);r.setCashAfterTradePct(20.0);r.setPortfolioDrawdownPct(5.0);r.setGoldPositionLimitPct(5.0);
        r.setSpecialChecks(new LinkedHashMap<>(Map.of("cashRecovery",PASS,"valuationCompression",PASS,"goldExposure",PASS)));
        return r;
    }
    private AnalysisReportDTO report(String code,PersonalModelReview review,String hash) {
        var model=catalog.definition();var asset=catalog.asset(code);
        var a=new PersonalModelAssessment(model.id(),model.version(),model.versionDate(),hash,asset,"S4","测试",true,"TIER_2",32.0,
                List.of(),new PersonalModelAssessment.Timing(null,null,null,null,null,null,null),List.of(),List.of(),List.of(),review,LocalDate.now(),null);
        return AnalysisReportDTO.builder().id("source-report").stockCode(code).stockName(asset.name()).personalModel(a).createdAt(LocalDateTime.now()).build();
    }
}
