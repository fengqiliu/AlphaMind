package com.alphamind.investment;

import com.alphamind.market.provider.*;
import com.alphamind.market.service.*;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.service.AnalysisHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalModelAnalysisServiceTest {
    @Test void unconfirmedPriceTriggerIsSavedWithoutInventedTargetOrConfidence() throws Exception {
        var market = mock(ResilientMarketDataService.class); var history = mock(AnalysisHistoryService.class);
        var catalog = new PersonalModelCatalog(new ObjectMapper());
        var service = new PersonalModelAnalysisService(catalog, new PersonalModelEngine(), market, history, new PersonalReviewReuseService(catalog, history));
        var quote = new MarketQuote("002352","顺丰控股",29.5,29.5,29.5,30,29,100,2950,LocalDateTime.now());
        var snapshot = new MarketDataSnapshot("002352","顺丰控股",quote,List.of(new DailyKline(LocalDate.now(),29.5,29.5,29,30,100,2950,1)),"fixture","fixture",Instant.now());
        when(market.fetch("002352","顺丰控股")).thenReturn(new MarketDataResolution(snapshot,MarketDataResolution.Status.LIVE,0,null));
        var report = service.analyze("002352",null);
        assertEquals("S1",report.getPersonalModel().level()); assertFalse(report.getPersonalModel().entryCandidate());
        assertNull(report.getTradeSignal().getTargetPrice()); assertNull(report.getTradeSignal().getStopLoss()); assertNull(report.getConfidence());
        var capture = ArgumentCaptor.forClass(AnalysisReportDTO.class); verify(history).save(capture.capture()); assertSame(report,capture.getValue());
        doThrow(new IllegalStateException("db failure")).when(history).save(any());
        assertThrows(IllegalStateException.class,() -> service.analyze("002352",null));
    }
    @Test void rejectsOutOfModelStocksBeforeNetworkOrDatabase() throws Exception {
        var market = mock(ResilientMarketDataService.class); var history = mock(AnalysisHistoryService.class);
        var service = new PersonalModelAnalysisService(new PersonalModelCatalog(new ObjectMapper()),new PersonalModelEngine(),market,history,new PersonalReviewReuseService(new PersonalModelCatalog(new ObjectMapper()),history));
        assertThrows(IllegalArgumentException.class,()->service.analyze("600519",null)); verifyNoInteractions(market,history);
    }
}
