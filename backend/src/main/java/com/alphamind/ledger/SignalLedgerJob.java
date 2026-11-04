package com.alphamind.ledger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每个交易日收盘后补算已到期的信号结果。默认开启，可用 ALPHAMIND_LEDGER_CRON 修改，设为 "-" 关闭。
 * 与手动触发共用同一把锁，避免并发写入同一批结果。
 */
@Slf4j
@Component
public class SignalLedgerJob {

    private final SignalLedgerService ledger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SignalLedgerJob(SignalLedgerService ledger) {
        this.ledger = ledger;
    }

    @Scheduled(cron = "${alphamind.ledger.cron:0 10 16 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduled() {
        runOnce().ifPresent(summary -> log.info("信号账本补算完成: 写入 {} 条，未到期 {} 条，取数失败股票 {}",
                summary.written(), summary.pending(), summary.failedStocks()));
    }

    /** 跑一轮；上一轮未结束或出错时返回 empty。 */
    public Optional<SignalLedgerService.Summary> runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("信号账本补算仍在运行，跳过本次触发");
            return Optional.empty();
        }
        try {
            return Optional.of(ledger.evaluatePending());
        } catch (RuntimeException e) {
            log.error("信号账本补算失败", e);
            return Optional.empty();
        } finally {
            running.set(false);
        }
    }
}
