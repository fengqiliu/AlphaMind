package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.repository.AnalysisReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 报告历史的事务与查询规则；分析及模型调用不占用此事务。 */
@Service
@RequiredArgsConstructor
public class AnalysisHistoryService {

    private static final int MAX_HISTORY = 50;

    private final AnalysisReportRepository repository;
    private final AnalysisReportMapper mapper;

    @Transactional
    public void save(AnalysisReportDTO report) {
        repository.saveAndFlush(mapper.toEntity(report));
    }

    @Transactional(readOnly = true)
    public List<AnalysisReportDTO> getRecent(String stockCode, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_HISTORY)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return (stockCode == null || stockCode.isBlank()
                ? repository.findAll(page)
                : repository.findByStockCode(stockCode, page))
                .map(mapper::toDTO)
                .getContent();
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisReportDTO> getReport(String reportId) {
        return repository.findById(reportId).map(mapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisReportDTO> getLatestPersonalReview(String stockCode) {
        return repository.findLatestPersonalReview(stockCode).map(mapper::toDTO);
    }
}
