package com.alphamind.archive;

import com.alphamind.news.service.StockNewsService;
import com.alphamind.service.StockCatalog;
import com.alphamind.service.StockCatalog.StockMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 每日对股票池逐只读取新闻；真实抓到的新闻由 {@link StockNewsService} 旁路存档。
 * 默认关闭：设置 {@code ALPHAMIND_ARCHIVE_CRON}（Spring cron，例如 "0 30 15 * * MON-FRI"）后启用。
 */
@Slf4j
@Component
public class NewsArchiveJob {

    public record Summary(int visited, int withNews, int failed) {}

    private final Supplier<Collection<StockMetadata>> pool;
    private final StockNewsService newsService;
    private final long delayMs;
    private final Consumer<Long> sleeper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public NewsArchiveJob(StockCatalog catalog, StockNewsService newsService,
                          @Value("${alphamind.archive.delay-ms:300}") long delayMs) {
        this(catalog::all, newsService, delayMs, NewsArchiveJob::sleep);
    }

    NewsArchiveJob(Supplier<Collection<StockMetadata>> pool, StockNewsService newsService, long delayMs,
                   Consumer<Long> sleeper) {
        this.pool = pool;
        this.newsService = newsService;
        this.delayMs = delayMs;
        this.sleeper = sleeper;
    }

    /** cron 为 "-" 时 Spring 不会调度该方法。 */
    @Scheduled(cron = "${alphamind.archive.cron:-}", zone = "Asia/Shanghai")
    public void scheduled() {
        Summary summary = runOnce();
        log.info("新闻存档任务完成: 访问 {} 只，取得新闻 {} 只，失败 {} 只",
                summary.visited(), summary.withNews(), summary.failed());
    }

    /** 跑一轮；上一轮尚未结束时直接跳过并返回空汇总。 */
    public Summary runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("新闻存档任务仍在运行，跳过本次触发");
            return new Summary(0, 0, 0);
        }
        try {
            int visited = 0, withNews = 0, failed = 0;
            var stocks = pool.get().stream().toList();
            for (int i = 0; i < stocks.size(); i++) {
                visited++;
                try {
                    if (newsService.news(stocks.get(i).code()).isPresent()) {
                        withNews++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("新闻存档读取失败，继续下一只: stockCode={}, reason={}", stocks.get(i).code(), e.getMessage());
                }
                if (i < stocks.size() - 1 && delayMs > 0) {
                    sleeper.accept(delayMs);
                }
            }
            return new Summary(visited, withNews, failed);
        } finally {
            running.set(false);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
