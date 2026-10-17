package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.SSEEvent;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** 同步与流式入口共用的分析用例：报告提交成功后才返回最终结果。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final PipelineOrchestrator pipelineOrchestrator;
    private final AnalysisHistoryService historyService;

    public AnalysisReportDTO analyze(String stockCode, String stockName, StrategyType strategy,
                                     AnalysisMode mode, Consumer<SSEEvent> eventConsumer) {
        AnalysisReportDTO report = pipelineOrchestrator.execute(
                stockCode, stockName, strategy, mode, eventConsumer);
        try {
            historyService.save(report);
        } catch (RuntimeException exception) {
            log.error("保存分析报告失败: id={}", report.getId(), exception);
            throw new IllegalStateException("分析报告保存失败，请稍后重试", exception);
        }
        return report;
    }
}
