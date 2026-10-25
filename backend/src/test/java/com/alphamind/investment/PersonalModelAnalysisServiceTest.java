package com.alphamind.investment;

import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;
import com.alphamind.fundamental.service.FundamentalDataService;
import com.alphamind.fundamental.service.FundamentalResolution;
import com.alphamind.fundamental.service.FundamentalSnapshot;
import com.alphamind.market.provider.*;
import com.alphamind.market.service.*;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.service.AnalysisHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalModelAnalysisServiceTest {
    @Test void unconfirmedPriceTriggerIsSavedWithoutInventedTargetOrConfidence() throws Exception {
        var market = mock(ResilientMarketDataService.class); var history = mock(AnalysisHistoryService.class);
        var catalog = new PersonalModelCatalog(new ObjectMapper());
        var service = new PersonalModelAnalysisService(catalog, new PersonalModelEngine(), market, history, new PersonalReviewReuseService(catalog, history), mock(FundamentalDataService.class));
        var quote = new MarketQuote("002352","顺丰控股",29.5,29.5,29.5,30,29,100,2950,LocalDateTime.now());
        var snapshot = new MarketDataSnapshot("002352","顺丰控股",quote,List.of(new DailyKline(LocalDate.now(),29.5,29.5,29,30,100,2950,1)),"fixture","fixture",Instant.now());
        when(market.fetch("002352","顺丰控股")).thenReturn(new MarketDataResolution(snapshot,MarketDataResolution.Status.LIVE,0,null));
        var report = service.analyze("002352",null);
        assertEquals("S1",report.getPersonalModel().level()); assertFalse(report.getPersonalModel().entryCandidate());
        assertNull(report.getPersonalModel().fundamentals());
        assertTrue(report.getPersonalModel().notes().stream().anyMatch(note -> note.contains("财报/估值自动数据不可用")));
        assertNull(report.getTradeSignal().getTargetPrice()); assertNull(report.getTradeSignal().getStopLoss()); assertNull(report.getConfidence());
        var capture = ArgumentCaptor.forClass(AnalysisReportDTO.class); verify(history).save(capture.capture()); assertSame(report,capture.getValue());
        doThrow(new IllegalStateException("db failure")).when(history).save(any());
        assertThrows(IllegalStateException.class,() -> service.analyze("002352",null));
    }
    @Test void rejectsOutOfModelStocksBeforeNetworkOrDatabase() throws Exception {
        var market = mock(ResilientMarketDataService.class); var history = mock(AnalysisHistoryService.class);
        var service = new PersonalModelAnalysisService(new PersonalModelCatalog(new ObjectMapper()),new PersonalModelEngine(),market,history,new PersonalReviewReuseService(new PersonalModelCatalog(new ObjectMapper()),history),mock(FundamentalDataService.class));
        assertThrows(IllegalArgumentException.class,()->service.analyze("600519",null)); verifyNoInteractions(market,history);
    }
    @Test void fundamentalsAreFrozenIntoReportWithoutChangingGates() throws Exception {
        var market = mock(ResilientMarketDataService.class); var history = mock(AnalysisHistoryService.class);
        var fundamentals = mock(FundamentalDataService.class);
        var catalog = new PersonalModelCatalog(new ObjectMapper());
        var service = new PersonalModelAnalysisService(catalog, new PersonalModelEngine(), market, history, new PersonalReviewReuseService(catalog, history), fundamentals);
        var quote = new MarketQuote("002352","顺丰控股",29.5,29.5,29.5,30,29,100,2950,LocalDateTime.now());
        var snapshot = new MarketDataSnapshot("002352","顺丰控股",quote,List.of(new DailyKline(LocalDate.now(),29.5,29.5,29,30,100,2950,1)),"fixture","fixture",Instant.now());
        when(market.fetch("002352","顺丰控股")).thenReturn(new MarketDataResolution(snapshot,MarketDataResolution.Status.LIVE,0,null));
        var valuation = new ValuationMetrics(LocalDate.now(), 29.5, 1.4e11, 12.0, 1.5, 6.0, 0.5, 1.1, 8.0, 10.0, 1200, 1200, LocalDate.now().minusYears(5));
        var financials = new FinancialMetrics("2026中报", LocalDate.of(2026,6,30), LocalDate.of(2026,8,28), "通用",
                1.2, 1.1, 2.4, 1.4e11, 5.6e9, 5.2e9, 9.0, 11.0, 10.5, 7.2, 13.0, 55.0);
        when(fundamentals.fundamentals("002352")).thenReturn(Optional.of(new FundamentalResolution(
                new FundamentalSnapshot("002352", valuation, financials, "eastmoney-datacenter", Instant.now()),
                FundamentalResolution.Status.LIVE, 0, null)));

        var assessment = service.analyze("002352", null).getPersonalModel();

        assertNotNull(assessment.fundamentals());
        assertEquals(8.0, assessment.fundamentals().getValuation().pePercentile());
        assertTrue(assessment.fundamentals().getEvidenceSummary().contains("2026中报"));
        // 即使 PE 位于 8% 分位，估值门也必须保持待人工确认
        assertEquals("PENDING", assessment.gates().stream().filter(g -> g.id().equals("VALUATION")).findFirst().orElseThrow().status());
        assertEquals("S1", assessment.level());
    }
}
