package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    private final PipelineOrchestrator pipeline = mock(PipelineOrchestrator.class);
    private final AnalysisHistoryService history = mock(AnalysisHistoryService.class);
    private final AnalysisService service = new AnalysisService(pipeline, history);

    @Test
    void returnsReportOnlyAfterHistoryHasCommitted() {
        AnalysisReportDTO report = AnalysisReportDTO.builder().id("report-1").build();
        when(pipeline.execute("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null)).thenReturn(report);

        assertSame(report, service.analyze("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null));
        var order = inOrder(pipeline, history);
        order.verify(pipeline).execute("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null);
        order.verify(history).save(report);
    }

    @Test
    void reportsPersistenceFailureWithoutLeakingDatabaseDetails() {
        AnalysisReportDTO report = AnalysisReportDTO.builder().id("report-2").build();
        when(pipeline.execute("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null)).thenReturn(report);
        doThrow(new DataAccessResourceFailureException("private database details")).when(history).save(report);

        var failure = assertThrows(IllegalStateException.class,
                () -> service.analyze("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null));
        assertEquals("分析报告保存失败，请稍后重试", failure.getMessage());
        assertInstanceOf(DataAccessResourceFailureException.class, failure.getCause());
    }

    @Test
    void failedAnalysisDoesNotWriteHistory() {
        when(pipeline.execute("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null))
                .thenThrow(new IllegalStateException("行情不可用"));
        assertThrows(IllegalStateException.class,
                () -> service.analyze("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, null));
        verifyNoInteractions(history);
    }
}
