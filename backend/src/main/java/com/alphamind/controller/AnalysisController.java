package com.alphamind.controller;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.StrategyType;
import com.alphamind.service.PipelineOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 分析控制器 - 处理股票分析请求，支持SSE流式响应
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final PipelineOrchestrator pipelineOrchestrator;
    private final ObjectMapper objectMapper;

    // 内存中的分析历史（最近50条）
    private final Deque<AnalysisReportDTO> analysisHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 50;

    /**
     * SSE流式分析
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAnalysis(
            @RequestParam String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false, defaultValue = "BALANCED") StrategyType strategy,
            @RequestParam(required = false, defaultValue = "true") Boolean enableDebate,
            @RequestParam(required = false) String sessionId) {

        String finalSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        final String finalStockName = stockName != null ? stockName : stockCode;

        log.info("收到分析请求: stockCode={}, stockName={}, strategy={}, enableDebate={}, sessionId={}",
                stockCode, finalStockName, strategy, enableDebate, finalSessionId);

        return Flux.create(emitter -> {
            try {
                // 执行分析
                AnalysisReportDTO report = pipelineOrchestrator.execute(
                        stockCode, finalStockName, strategy, enableDebate,
                        event -> {
                            try {
                                String json = objectMapper.writeValueAsString(event);
                                // 使用具名 SSE 事件格式：event: <type>\ndata: <json>\n\n
                                String eventName = event.getEvent() != null ? event.getEvent() : "message";
                                emitter.next("event: " + eventName + "\ndata: " + json + "\n\n");
                            } catch (Exception e) {
                                log.error("序列化SSE事件失败", e);
                            }
                        }
                );

                // 发送最终结果
                try {
                    String resultJson = objectMapper.writeValueAsString(
                            ApiResponse.success("分析完成", report));
                    emitter.next("event: result\ndata: " + resultJson + "\n\n");
                } catch (Exception e) {
                    log.error("序列化结果失败", e);
                }

                // 保存到历史记录
                saveToHistory(report);

                emitter.complete();

            } catch (Exception e) {
                log.error("分析失败: stockCode={}", stockCode, e);
                try {
                    String errorJson = objectMapper.writeValueAsString(
                            ApiResponse.error(e.getMessage()));
                    emitter.next("event: error\ndata: " + errorJson + "\n\n");
                } catch (Exception ex) {
                    // ignore
                }
                emitter.error(e);
            }
        });
    }

    /**
     * 同步分析
     */
    @PostMapping("/analyze")
    public ApiResponse<AnalysisReportDTO> analyze(@Valid @RequestBody AnalysisRequest request) {
        log.info("收到同步分析请求: {}", request);

        try {
            AnalysisReportDTO report = pipelineOrchestrator.execute(
                    request.getStockCode(),
                    request.getStockCode(), // 使用code作为name的默认值
                    request.getStrategy() != null ? request.getStrategy() : StrategyType.BALANCED,
                    request.getEnableDebate() != null ? request.getEnableDebate() : true,
                    null // 同步模式不需要回调
            );

            saveToHistory(report);
            return ApiResponse.success("分析完成", report);
        } catch (Exception e) {
            log.error("分析失败: {}", request.getStockCode(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 分析历史记录
     */
    @GetMapping("/history")
    public ApiResponse<List<AnalysisReportDTO>> getHistory(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<AnalysisReportDTO> history = analysisHistory.stream()
                .filter(r -> stockCode == null || stockCode.equals(r.getStockCode()))
                .limit(Math.min(limit, MAX_HISTORY))
                .toList();

        return ApiResponse.success(history);
    }

    private void saveToHistory(AnalysisReportDTO report) {
        if (report == null) return;
        analysisHistory.addFirst(report);
        while (analysisHistory.size() > MAX_HISTORY) {
            analysisHistory.pollLast();
        }
    }
}
