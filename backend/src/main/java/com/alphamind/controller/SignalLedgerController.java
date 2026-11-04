package com.alphamind.controller;

import com.alphamind.ledger.SignalLedgerJob;
import com.alphamind.ledger.SignalLedgerService;
import com.alphamind.ledger.SignalOutcomeEntity;
import com.alphamind.ledger.SignalOutcomeRepository;
import com.alphamind.ledger.SignalPerformanceService;
import com.alphamind.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 信号账本：事后表现汇总、单份报告的事后结果、手动补算。 */
@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class SignalLedgerController {

    private final SignalPerformanceService performance;
    private final SignalOutcomeRepository outcomes;
    private final SignalLedgerJob job;

    @GetMapping("/performance")
    public ApiResponse<SignalPerformanceService.Summary> performance() {
        return ApiResponse.success(performance.summary());
    }

    @GetMapping("/outcomes/{reportId}")
    public ApiResponse<List<SignalOutcomeEntity>> outcomes(@PathVariable String reportId) {
        return ApiResponse.success(outcomes.findByReportIdOrderByHorizonDays(reportId));
    }

    /** 立即补算一次（与定时任务互斥）；上一轮仍在运行时返回 409。 */
    @PostMapping("/evaluate")
    public ApiResponse<SignalLedgerService.Summary> evaluate() {
        return job.runOnce()
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(409, "补算正在进行或执行失败，请查看日志后稍后重试"));
    }
}
