package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.entity.AnalysisReportEntity;
import com.alphamind.repository.AnalysisReportRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisHistoryServiceTest {
    private final AnalysisReportRepository repository = mock(AnalysisReportRepository.class);
    private final AnalysisReportMapper mapper = mock(AnalysisReportMapper.class);
    private final AnalysisHistoryService service = new AnalysisHistoryService(repository, mapper);

    @Test
    void flushesReportBeforeReturning() {
        AnalysisReportDTO report = new AnalysisReportDTO();
        AnalysisReportEntity entity = new AnalysisReportEntity();
        when(mapper.toEntity(report)).thenReturn(entity);
        service.save(report);
        verify(repository).saveAndFlush(entity);
    }

    @Test
    void boundsFilteredHistoryAtDatabaseLevel() {
        when(repository.findByStockCode(eq("600519"), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(service.getRecent("600519", 1000).isEmpty());
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStockCode(eq("600519"), page.capture());
        assertEquals(50, page.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("createdAt").getDirection());
        verify(repository, never()).findByStockCodeOrderByCreatedAtDesc(anyString());
    }

    @Test
    void keepsNonpositiveLimitsBounded() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        service.getRecent(null, -1);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }
    @Test
    void reportLookupDoesNotDependOnRecentListLimit() {
        AnalysisReportEntity entity = new AnalysisReportEntity();
        AnalysisReportDTO report = new AnalysisReportDTO();
        when(repository.findById("older-report")).thenReturn(java.util.Optional.of(entity));
        when(mapper.toDTO(entity)).thenReturn(report);
        assertSame(report, service.getReport("older-report").orElseThrow());
        verify(repository).findById("older-report");
        verify(repository, never()).findAll(any(Pageable.class));
    }
}
