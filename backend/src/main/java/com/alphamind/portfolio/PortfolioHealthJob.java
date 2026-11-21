package com.alphamind.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作日收盘后跑一次持仓体检，排在 15:30 净值快照之后。默认 15:40（上海时区），
 * 可用 ALPHAMIND_HEALTH_CRON 修改，设为 "-" 关闭。出错只记日志，不影响其他定时任务。
 */
@Slf4j
@Component
public class PortfolioHealthJob {
    private final PortfolioHealthAlertService service;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PortfolioHealthJob(PortfolioHealthAlertService service) {
        this.service = service;
    }

    @Scheduled(cron = "${alphamind.health.cron:0 40 15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduled() {
        runOnce().ifPresent(summary -> {
            if (summary.outcome() == PortfolioHealthAlertService.Outcome.CHECKED) {
                log.info("持仓体检: 触发项 {} 个，新提醒 {} 条，恢复 {} 项，无法检查 {}",
                        summary.findings(), summary.created(), summary.cleared(), summary.unavailable());
            }
        });
    }

    /** 跑一轮；上一轮未结束或出错时返回 empty。 */
    public Optional<PortfolioHealthAlertService.Summary> runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("持仓体检仍在运行，跳过本次触发");
            return Optional.empty();
        }
        try {
            return Optional.of(service.check());
        } catch (RuntimeException e) {
            log.error("持仓体检失败", e);
            return Optional.empty();
        } finally {
            running.set(false);
        }
    }
}
