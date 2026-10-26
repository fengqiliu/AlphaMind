package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.EastmoneyKlineMarketDataProvider;
import com.alphamind.service.StockCatalog;
import com.alphamind.service.StockCatalog.StockMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 技术分离线回测入口（不启动 Spring）。在 backend 目录运行：
 * {@code java -cp target/classes:<deps> com.alphamind.backtest.TechnicalBacktestMain [--refresh] [--out=路径]}。
 * K 线缓存在 {@code backtest-data/}，再次运行默认离线读取缓存。
 */
public final class TechnicalBacktestMain {
    static final int WINDOW = 120;
    static final int[] HORIZONS = {5, 10, 20};
    static final int VERDICT_HORIZON = 10;
    static final double IN_SAMPLE_SHARE = 0.7;
    static final int BARS_TO_FETCH = 1250;
    static final long REQUEST_INTERVAL_MS = 300;

    private TechnicalBacktestMain() {}

    public static RunSummary run(Collection<StockMetadata> universe, KlineSource source, LocalDate runDate) {
        int minimumBars = WINDOW + Arrays.stream(HORIZONS).max().orElseThrow();
        List<SymbolCoverage> coverage = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<BacktestSample> samples = new ArrayList<>();
        int limitUpSkipped = 0;
        for (StockMetadata stock : universe) {
            Optional<List<DailyKline>> loaded = source.load(stock.code());
            if (loaded.isEmpty()) {
                excluded.add(stock.code() + " " + stock.name() + "：取数失败");
                continue;
            }
            List<DailyKline> bars = loaded.get();
            if (bars.size() < minimumBars) {
                excluded.add(stock.code() + " " + stock.name() + "：仅 " + bars.size() + " 根，少于 " + minimumBars + " 根");
                continue;
            }
            ReplayResult result = TechnicalReplay.replay(stock.code(), bars, WINDOW, HORIZONS, Costs.DEFAULT);
            samples.addAll(result.samples());
            limitUpSkipped += result.limitUpSkipped();
            coverage.add(new SymbolCoverage(stock.code(), stock.name(), bars.get(0).tradeDate(),
                    bars.get(bars.size() - 1).tradeDate(), bars.size()));
        }

        LocalDate split = BacktestStats.splitDate(samples, IN_SAMPLE_SHARE);
        List<BacktestStats.ExcessSample> rows = BacktestStats.withExcess(samples);
        List<BacktestStats.ExcessSample> inRows = rows.stream().filter(r -> r.sample().signalDate().isBefore(split)).toList();
        List<BacktestStats.ExcessSample> outRows = rows.stream().filter(r -> !r.sample().signalDate().isBefore(split)).toList();

        Map<Integer, List<BucketStats>> in = new TreeMap<>(), out = new TreeMap<>(), fine = new TreeMap<>();
        Map<Integer, IcStats> icIn = new TreeMap<>(), icOut = new TreeMap<>();
        for (int h : HORIZONS) {
            in.put(h, BacktestStats.buckets(inRows, h, BacktestStats.STANDARD_RANGES));
            out.put(h, BacktestStats.buckets(outRows, h, BacktestStats.STANDARD_RANGES));
            fine.put(h, BacktestStats.buckets(outRows, h, BacktestStats.FINE_RANGES));
            icIn.put(h, BacktestStats.ic(inRows.stream().map(BacktestStats.ExcessSample::sample).toList(), h));
            icOut.put(h, BacktestStats.ic(outRows.stream().map(BacktestStats.ExcessSample::sample).toList(), h));
        }
        List<BucketStats> verdictBuckets = out.get(VERDICT_HORIZON);
        Verdict verdict = coverage.size() < BacktestStats.MIN_STOCKS_PER_DATE ? Verdict.NONE
                : BacktestStats.verdict(verdictBuckets.get(0), verdictBuckets.get(verdictBuckets.size() - 1), icOut.get(VERDICT_HORIZON));
        return new RunSummary(runDate, coverage, excluded, in, out, fine, icIn, icOut, split, limitUpSkipped, verdict);
    }

    public static void main(String[] args) throws IOException {
        boolean refresh = Arrays.asList(args).contains("--refresh");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Path out = Arrays.stream(args).filter(a -> a.startsWith("--out=")).findFirst()
                .map(a -> Path.of(a.substring("--out=".length())))
                .orElse(Path.of("..", "docs", "回测", "技术分回测-" + today + ".md"));

        ObjectMapper mapper = new ObjectMapper();
        var provider = new EastmoneyKlineMarketDataProvider(new RestTemplateBuilder(), mapper, 3000, 15000);
        KlineSource source = new CachedKlineSource(Path.of("backtest-data"), code -> {
            System.out.println("取数 " + code);
            try {
                Thread.sleep(REQUEST_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return provider.fetchDailyKlines(code, BARS_TO_FETCH);
        }, refresh, mapper);

        RunSummary summary = run(new StockCatalog().all(), source, today);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, BacktestReport.render(summary));
        System.out.println("判定：" + summary.verdict().label() + "；参与 " + summary.coverage().size()
                + " 只，排除 " + summary.excluded().size() + " 只；报告：" + out.toAbsolutePath().normalize());
    }
}
