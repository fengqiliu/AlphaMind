package com.alphamind.controller;

import com.alphamind.model.dto.ApiResponse;
import com.alphamind.portfolio.PortfolioHealth;
import com.alphamind.portfolio.PortfolioHealthAlertEntity;
import com.alphamind.portfolio.PortfolioHealthAlertService;
import com.alphamind.portfolio.PortfolioProfile;
import com.alphamind.portfolio.PortfolioService;
import com.alphamind.portfolio.PortfolioSnapshotService;
import com.alphamind.portfolio.RiskBudget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 当前组合（手动维护）与个人模型 Gate 5 的推算。 */
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService service;
    private final PortfolioSnapshotService snapshots;
    private final PortfolioHealthAlertService healthAlerts;

    public record BudgetRequest(@NotNull @Pattern(regexp = "\\d{6}") String stockCode,
                                @NotNull @DecimalMin("0") Double plannedBuyAmount) {}

    @GetMapping
    public ApiResponse<PortfolioProfile> get() {
        return service.get().map(ApiResponse::success).orElseGet(() -> ApiResponse.error(404, "尚未录入持仓"));
    }

    @PutMapping
    public ApiResponse<PortfolioProfile> save(@Valid @RequestBody PortfolioProfile profile) {
        try { return ApiResponse.success(service.save(profile)); }
        catch (IllegalArgumentException failure) { return ApiResponse.error(400, failure.getMessage()); }
    }

    /** 持仓体检：越线、过热与证据过期提示，只读。 */
    @GetMapping("/health")
    public ApiResponse<PortfolioHealth> health() {
        return service.health().map(ApiResponse::success).orElseGet(() -> ApiResponse.error(404, "尚未录入持仓"));
    }

    /** 每日体检产生的提醒（新出现的越线项），默认只列未读，最多 50 条。 */
    @GetMapping("/health/alerts")
    public ApiResponse<List<PortfolioHealthAlertEntity>> healthAlerts(
            @RequestParam(defaultValue = "false") boolean includeAcknowledged) {
        return ApiResponse.success(healthAlerts.list(includeAcknowledged));
    }

    @GetMapping("/health/alerts/unread-count")
    public ApiResponse<Map<String, Long>> healthAlertUnreadCount() {
        return ApiResponse.success(Map.of("count", healthAlerts.unreadCount()));
    }

    @PostMapping("/health/alerts/{id}/ack")
    public ApiResponse<PortfolioHealthAlertEntity> acknowledgeHealthAlert(@PathVariable long id) {
        return healthAlerts.acknowledge(id).map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(404, "提醒不存在"));
    }

    /** 组合每日快照（总资产与回撤曲线），按日期升序。days 限制在 1 到 1000 天。 */
    @GetMapping("/snapshots")
    public ApiResponse<List<PortfolioSnapshotService.Point>> snapshots(@RequestParam(defaultValue = "180") int days) {
        return ApiResponse.success(snapshots.history(Math.max(1, Math.min(days, 1000))));
    }

    @PostMapping("/risk-budget")
    public ApiResponse<RiskBudget> riskBudget(@Valid @RequestBody BudgetRequest request) {
        try {
            return service.riskBudget(request.stockCode(), request.plannedBuyAmount()).map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.error(404, "尚未录入持仓，请先在“我的持仓”中录入"));
        } catch (IllegalArgumentException failure) {
            return ApiResponse.error(400, failure.getMessage());
        }
    }
}
