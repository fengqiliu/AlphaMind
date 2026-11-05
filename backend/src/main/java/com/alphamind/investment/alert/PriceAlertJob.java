package com.alphamind.investment.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 交易时段每 15 分钟检查一次档位。默认开启，可用 ALPHAMIND_ALERTS_CRON 修改，设为 "-" 关闭。
 * 与手动触发共用同一把锁，避免同一档位被并发写出两条提醒。
 */
@Slf4j
@Component
public class PriceAlertJob {
    private final PriceAlertService service;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PriceAlertJob(PriceAlertService service) {
        this.service = service;
    }

    @Scheduled(cron = "${alphamind.alerts.cron:0 */15 9-15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduled() {
        runOnce().ifPresent(summary -> {
            if (summary.created() > 0 || !summary.failed().isEmpty() || !summary.skippedStale().isEmpty()) {
                log.info("价格档位检查: 检查 {} 个，新提醒 {} 条，过期缓存跳过 {}，取数失败 {}",
                        summary.checked(), summary.created(), summary.skippedStale(), summary.failed());
            }
        });
    }

    /** 跑一轮；上一轮未结束或出错时返回 empty。 */
    public Optional<PriceAlertService.Summary> runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("价格档位检查仍在运行，跳过本次触发");
            return Optional.empty();
        }
        try {
            return Optional.of(service.checkAll());
        } catch (RuntimeException e) {
            log.error("价格档位检查失败", e);
            return Optional.empty();
        } finally {
            running.set(false);
        }
    }
}
