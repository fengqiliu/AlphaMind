package com.alphamind.controller;

import com.alphamind.model.dto.*;
import com.alphamind.model.entity.AnalysisReportEntity;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.StrategyType;
import com.alphamind.repository.AnalysisReportRepository;
import com.alphamind.service.PipelineOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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
    private final AnalysisReportRepository analysisReportRepository;

    private static final int MAX_HISTORY = 50;

    /**
     * SSE流式分析
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAnalysis(
            @RequestParam String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false, defaultValue = "BALANCED") StrategyType strategy,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Boolean enableDebate,
            @RequestParam(required = false) String sessionId) {

        String finalSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        final String finalStockName = stockName != null ? stockName : stockCode;

        AnalysisMode resolvedMode = resolveMode(mode, enableDebate);

        log.info("收到分析请求: stockCode={}, stockName={}, strategy={}, enableDebate={}, sessionId={}",
            stockCode, finalStockName, strategy, resolvedMode == AnalysisMode.DEBATE, finalSessionId);

        return Flux.create(emitter -> {
            try {
                // 执行分析
                AnalysisReportDTO report = pipelineOrchestrator.execute(
                    stockCode, finalStockName, strategy, resolvedMode,
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
                    resolveMode(request.getMode(), request.getEnableDebate()),
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
    @Transactional(readOnly = true)
    public ApiResponse<List<AnalysisReportDTO>> getHistory(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<AnalysisReportEntity> entities = stockCode != null
                ? analysisReportRepository.findByStockCodeOrderByCreatedAtDesc(stockCode)
                : analysisReportRepository.findTop50ByOrderByCreatedAtDesc();

        List<AnalysisReportDTO> history = entities.stream()
                .limit(Math.min(limit, MAX_HISTORY))
                .map(this::toDTO)
                .toList();

        return ApiResponse.success(history);
    }

    @Transactional
    private void saveToHistory(AnalysisReportDTO report) {
        if (report == null) return;
        try {
            AnalysisReportEntity entity = toEntity(report);
            analysisReportRepository.save(entity);
        } catch (Exception e) {
            log.error("保存分析报告失败: id={}", report.getId(), e);
        }
    }

    private AnalysisReportEntity toEntity(AnalysisReportDTO dto) {
        AnalysisReportEntity.AnalysisReportEntityBuilder builder = AnalysisReportEntity.builder()
                .id(dto.getId())
                .stockCode(dto.getStockCode())
                .stockName(dto.getStockName() != null ? dto.getStockName() : dto.getStockCode())
                .marketData(dto.getMarketData())
                .technicalIndicators(dto.getTechnicalIndicators())
                .sentimentData(dto.getSentimentData())
                .judgment(dto.getJudgment());

        if (dto.getFinalSignal() != null) {
            builder.signalType(dto.getFinalSignal().name());
        }
        if (dto.getConfidence() != null) {
            if (dto.getConfidence().getValue() != null) {
                builder.confidenceValue(BigDecimal.valueOf(dto.getConfidence().getValue()));
            }
            if (dto.getConfidence().getLevel() != null) {
                builder.confidenceLevel(dto.getConfidence().getLevel().name());
            }
        }
        if (dto.getTradeSignal() != null) {
            TradeSignalDTO ts = dto.getTradeSignal();
            if (ts.getType() != null) builder.signalType(ts.getType().name());
            if (ts.getEntryPrice() != null) builder.entryPrice(BigDecimal.valueOf(ts.getEntryPrice()));
            if (ts.getTargetPrice() != null) builder.targetPrice(BigDecimal.valueOf(ts.getTargetPrice()));
            if (ts.getStopLoss() != null) builder.stopLoss(BigDecimal.valueOf(ts.getStopLoss()));
            builder.holdingDays(ts.getHoldingPeriodDays());
            builder.rationale(ts.getRationale());
        }
        if (dto.getCreatedAt() != null) {
            builder.createdAt(dto.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        }
        return builder.build();
    }

    private AnalysisReportDTO toDTO(AnalysisReportEntity entity) {
        return objectMapper.convertValue(
                objectMapper.createObjectNode()
                        .put("id", entity.getId())
                        .put("stockCode", entity.getStockCode())
                        .put("stockName", entity.getStockName()),
                AnalysisReportDTO.class
        );
    }

    /**
     * mode 优先级高于 enableDebate。
     * 为兼容旧调用：当两者都未传时，默认 DEBATE（与旧默认 enableDebate=true 一致）。
     */
    private AnalysisMode resolveMode(String mode, Boolean enableDebate) {
        if (mode != null && !mode.isBlank()) {
            return AnalysisMode.fromValue(mode);
        }
        if (enableDebate != null) {
            return enableDebate ? AnalysisMode.DEBATE : AnalysisMode.PIPELINE;
        }
        return AnalysisMode.DEBATE;
    }

    private AnalysisMode resolveMode(AnalysisMode mode, Boolean enableDebate) {
        if (mode != null) {
            return mode;
        }
        if (enableDebate != null) {
            return enableDebate ? AnalysisMode.DEBATE : AnalysisMode.PIPELINE;
        }
        return AnalysisMode.DEBATE;
    }
}
