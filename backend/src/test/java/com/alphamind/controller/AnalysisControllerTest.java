package com.alphamind.controller;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.AnalysisRequest;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import com.alphamind.service.AnalysisHistoryService;
import com.alphamind.service.AnalysisService;
import com.alphamind.strategy.StrategyModeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisControllerTest {
    private final AnalysisService service = mock(AnalysisService.class);
    private final AnalysisHistoryService history = mock(AnalysisHistoryService.class);
    private final AnalysisController controller = new AnalysisController(
            service, new ObjectMapper(), history, mock(StrategyModeResolver.class));

    @Test
    void successfulStreamEndsWithResultThenComplete() {
        when(service.analyze(eq("600519"), anyString(), any(), any(), any()))
                .thenReturn(AnalysisReportDTO.builder().id("saved-report").build());
        var events = controller.streamAnalysis("600519", null, StrategyType.BALANCED, "PIPELINE", null, null)
                .collectList().block();
        assertNotNull(events);
        assertEquals(List.of("result", "complete"), events.stream().map(ServerSentEvent::event).toList());
        assertTrue(events.get(0).data().contains("saved-report"));
    }

    @Test
    void persistenceFailureEmitsBusinessErrorWithoutSuccessEvents() {
        when(service.analyze(eq("600519"), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("分析报告保存失败，请稍后重试"));
        var events = controller.streamAnalysis("600519", null, StrategyType.BALANCED, "PIPELINE", null, null)
                .collectList().block();
        assertNotNull(events);
        assertEquals(List.of("error"), events.stream().map(ServerSentEvent::event).toList());
        assertTrue(events.get(0).data().contains("分析报告保存失败"));
    }

    @Test
    void synchronousAnalysisAlsoReportsPersistenceFailure() {
        when(service.analyze(eq("600519"), anyString(), any(), any(), isNull()))
                .thenThrow(new IllegalStateException("分析报告保存失败，请稍后重试"));
        AnalysisRequest request = new AnalysisRequest();
        request.setStockCode("600519");
        request.setStrategy(StrategyType.BALANCED);
        request.setMode(AnalysisMode.PIPELINE);
        var response = controller.analyze(request);
        assertEquals(500, response.getCode());
        assertNull(response.getData());
    }
    @Test
    void loadsReportByIdAndReportsMissingRecords() {
        AnalysisReportDTO report = AnalysisReportDTO.builder().id("saved-report").build();
        when(history.getReport("saved-report")).thenReturn(java.util.Optional.of(report));
        when(history.getReport("missing")).thenReturn(java.util.Optional.empty());
        assertSame(report, controller.getReport("saved-report").getData());
        assertEquals(404, controller.getReport("missing").getCode());
        assertNull(controller.getReport("missing").getData());
    }
}
