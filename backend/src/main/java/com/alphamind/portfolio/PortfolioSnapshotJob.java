package com.alphamind.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作日收盘后记录一次组合快照。默认 15:30（上海时区），可用 ALPHAMIND_SNAPSHOT_CRON 修改，设为 "-" 关闭。
 * 周内的休市日没有新行情，记下的是上一个收盘价对应的总资产，曲线上表现为一段持平。
 */
@Slf4j
@Component
public class PortfolioSnapshotJob {
    private final PortfolioSnapshotService service;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PortfolioSnapshotJob(PortfolioSnapshotService service) {
        this.service = service;
    }

    @Scheduled(cron = "${alphamind.snapshot.cron:0 30 15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduled() {
        runOnce().ifPresent(result -> {
            switch (result.outcome()) {
                case RECORDED -> log.info("组合快照已记录: date={}, totalValue={}", result.date(), result.totalValue());
                case INCOMPLETE -> log.warn("组合快照跳过（缺当期行情）: date={}, problems={}", result.date(), result.problems());
                case NO_PORTFOLIO -> log.debug("尚未录入持仓，不记录组合快照");
            }
        });
    }

    /** 跑一轮；上一轮未结束或出错时返回 empty。 */
    public Optional<PortfolioSnapshotService.Result> runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("组合快照仍在运行，跳过本次触发");
            return Optional.empty();
        }
        try {
            return Optional.of(service.record());
        } catch (RuntimeException e) {
            log.error("组合快照记录失败", e);
            return Optional.empty();
        } finally {
            running.set(false);
        }
    }
}
