package com.alphamind.controller;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import com.alphamind.service.AnalysisHistoryService;
import com.alphamind.service.AnalysisService;
import com.alphamind.strategy.StrategyModeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * 分析控制器 - 处理股票分析请求，支持SSE流式响应
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;
    private final AnalysisHistoryService historyService;
    private final StrategyModeResolver strategyModeResolver;

    /**
     * SSE流式分析
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAnalysis(
            @RequestParam String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false, defaultValue = "BALANCED") StrategyType strategy,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Boolean enableDebate,
            @RequestParam(required = false) String sessionId) {

        String finalSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        final String finalStockName = stockName != null ? stockName : stockCode;

        AnalysisMode resolvedMode = resolveMode(mode, enableDebate, strategy);

        log.info("收到分析请求: stockCode={}, stockName={}, strategy={}, enableDebate={}, sessionId={}",
            stockCode, finalStockName, strategy, resolvedMode == AnalysisMode.DEBATE, finalSessionId);

        return Flux.create(emitter -> {
            try {
                // 执行分析
                AnalysisReportDTO report = analysisService.analyze(
                    stockCode, finalStockName, strategy, resolvedMode,
                        event -> {
                            try {
                                String json = objectMapper.writeValueAsString(event);
                                // 使用具名 SSE 事件格式：event: <type>\ndata: <json>\n\n
                                String eventName = event.getEvent() != null ? event.getEvent() : "message";
                                emitter.next(ServerSentEvent.<String>builder()
                                        .event(eventName)
                                        .data(json)
                                        .build());
                            } catch (Exception e) {
                                log.error("序列化SSE事件失败", e);
                            }
                        }
                );

                // 用例已提交报告，再按 result → complete 的顺序结束 SSE。
                try {
                    String resultJson = objectMapper.writeValueAsString(
                            ApiResponse.success("分析完成", report));
                    emitter.next(ServerSentEvent.<String>builder()
                            .event("result")
                            .data(resultJson)
                            .build());

                    String completeJson = objectMapper.writeValueAsString(SSEEvent.completeEvent());
                    emitter.next(ServerSentEvent.<String>builder()
                            .event("complete")
                            .data(completeJson)
                            .build());
                } catch (Exception e) {
                    log.error("序列化结果失败", e);
                }

                emitter.complete();

            } catch (Exception e) {
                log.error("分析失败: stockCode={}", stockCode, e);
                try {
                    String errorJson = objectMapper.writeValueAsString(
                            ApiResponse.error(e.getMessage()));
                    emitter.next(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorJson)
                            .build());
                } catch (Exception ex) {
                    // ignore
                }
                emitter.complete();
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
            AnalysisReportDTO report = analysisService.analyze(
                    request.getStockCode(),
                    request.getStockCode(), // 使用code作为name的默认值
                    request.getStrategy() != null ? request.getStrategy() : StrategyType.BALANCED,
                    resolveMode(request.getMode(), request.getEnableDebate(),
                            request.getStrategy() != null ? request.getStrategy() : StrategyType.BALANCED),
                    null // 同步模式不需要回调
            );

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

        return ApiResponse.success(historyService.getRecent(stockCode, limit));
    }

    /** 按 ID 读取完整报告，不受最近历史列表数量限制。 */
    @GetMapping("/{reportId}")
    public ApiResponse<AnalysisReportDTO> getReport(@PathVariable String reportId) {
        return historyService.getReport(reportId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "报告不存在"));
    }

    /** mode 优先级高于 enableDebate，两者缺省时使用策略默认值。 */
    private AnalysisMode resolveMode(String mode, Boolean enableDebate, StrategyType strategy) {
        if (mode != null && !mode.isBlank()) {
            return AnalysisMode.fromValue(mode);
        }
        return strategyModeResolver.resolve(null, enableDebate, strategy);
    }

    private AnalysisMode resolveMode(AnalysisMode mode, Boolean enableDebate, StrategyType strategy) {
        return strategyModeResolver.resolve(mode, enableDebate, strategy);
    }
}
