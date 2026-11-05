package com.alphamind.controller;

import com.alphamind.investment.alert.PriceAlertEntity;
import com.alphamind.investment.alert.PriceAlertJob;
import com.alphamind.investment.alert.PriceAlertService;
import com.alphamind.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 个人模型价格提醒：列表、未读数、标记已读、手动检查。 */
@RestController
@RequestMapping("/api/v1/investment-model/alerts")
@RequiredArgsConstructor
public class PriceAlertController {
    private final PriceAlertService service;
    private final PriceAlertJob job;

    @GetMapping
    public ApiResponse<List<PriceAlertEntity>> list(@RequestParam(defaultValue = "false") boolean includeAcknowledged) {
        return ApiResponse.success(service.list(includeAcknowledged));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", service.unreadCount()));
    }

    @PostMapping("/{id}/ack")
    public ApiResponse<PriceAlertEntity> acknowledge(@PathVariable long id) {
        return service.acknowledge(id).map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "提醒不存在"));
    }

    /** 立即检查一次（与定时任务互斥）；上一轮仍在运行时返回 409。 */
    @PostMapping("/check")
    public ApiResponse<PriceAlertService.Summary> check() {
        return job.runOnce().map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(409, "检查正在进行或执行失败，请查看日志后稍后重试"));
    }
}
