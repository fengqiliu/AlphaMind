package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.EastmoneyKlineMarketDataProvider;
import com.alphamind.service.StockCatalog;
import com.alphamind.service.StockCatalog.StockMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/**
 * 技术分离线回测入口（不启动 Spring）。在 backend 目录运行：
 * {@code java -cp target/classes:<deps> com.alphamind.backtest.TechnicalBacktestMain [--refresh] [--out=路径] [--raw-dir=目录]}。
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
        List<String> etfs = new ArrayList<>();
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
            if (stock.market() != null && stock.market().contains("ETF")) etfs.add(stock.code() + " " + stock.name());
            coverage.add(new SymbolCoverage(stock.code(), stock.name(), bars.get(0).tradeDate(),
                    bars.get(bars.size() - 1).tradeDate(), bars.size()));
        }

        List<BacktestSample> usable = BacktestStats.dropThinDates(samples);
        LocalDate split = BacktestStats.splitDate(usable, IN_SAMPLE_SHARE);
        List<BacktestStats.ExcessSample> rows = BacktestStats.withExcess(usable);
        Map<Integer, List<BucketStats>> in = new TreeMap<>(), out = new TreeMap<>(), fine = new TreeMap<>();
        Map<Integer, IcStats> icIn = new TreeMap<>(), icOut = new TreeMap<>();
        for (int h : HORIZONS) {
            List<BacktestStats.ExcessSample> inRows = List.of(), outRows = List.of();
            if (split != null) {
                LocalDate inEnd = BacktestStats.embargoStart(usable, split, h);
                inRows = rows.stream().filter(r -> r.sample().signalDate().isBefore(inEnd)).toList();
                outRows = rows.stream().filter(r -> !r.sample().signalDate().isBefore(split)).toList();
            }
            in.put(h, BacktestStats.buckets(inRows, h, BacktestStats.STANDARD_RANGES));
            out.put(h, BacktestStats.buckets(outRows, h, BacktestStats.STANDARD_RANGES));
            fine.put(h, BacktestStats.buckets(outRows, h, BacktestStats.FINE_RANGES));
            icIn.put(h, BacktestStats.ic(inRows.stream().map(BacktestStats.ExcessSample::sample).toList(), h));
            icOut.put(h, BacktestStats.ic(outRows.stream().map(BacktestStats.ExcessSample::sample).toList(), h));
        }
        List<BucketStats> verdictBuckets = out.get(VERDICT_HORIZON);
        Verdict verdict = coverage.size() < BacktestStats.MIN_STOCKS_PER_DATE ? Verdict.INSUFFICIENT_DATA
                : BacktestStats.verdict(verdictBuckets.get(0), verdictBuckets.get(verdictBuckets.size() - 1), icOut.get(VERDICT_HORIZON));
        List<String> dataNotes = new ArrayList<>(source.notes());
        if (!etfs.isEmpty()) dataNotes.add("股票池含 ETF " + etfs.size() + " 只（按同一技术规则评分）：" + String.join("、", etfs));
        return new RunSummary(runDate, coverage, excluded, in, out, fine, icIn, icOut, dataNotes, split, limitUpSkipped, verdict);
    }

    /**
     * 读取事先保存的东方财富日 K 原始响应 {@code <dir>/<code>.json}，用线上同一解析器转换；
     * 用于无法直连数据源的环境（如需认证代理），文件缺失或无法解析时返回 empty。
     */
    static Function<String, Optional<List<DailyKline>>> rawResponseFetcher(Path dir, ObjectMapper mapper) {
        return code -> {
            Path file = dir.resolve(code + ".json");
            if (!Files.exists(file)) return Optional.empty();
            try {
                return EastmoneyKlineMarketDataProvider.parseKlines(Files.readString(file), BARS_TO_FETCH, mapper);
            } catch (IOException exception) {
                return Optional.empty();
            }
        };
    }

    public static void main(String[] args) throws IOException {
        boolean refresh = Arrays.asList(args).contains("--refresh");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Optional<Path> requestedOut = Arrays.stream(args).filter(a -> a.startsWith("--out=")).findFirst()
                .map(a -> Path.of(a.substring("--out=".length())));

        ObjectMapper mapper = new ObjectMapper();
        Optional<Path> rawDir = Arrays.stream(args).filter(a -> a.startsWith("--raw-dir=")).findFirst()
                .map(a -> Path.of(a.substring("--raw-dir=".length())));
        var provider = new EastmoneyKlineMarketDataProvider(
                // 命令行工具固定用 JDK HTTP 客户端，避免类路径上的 Netty 被自动选中
                new RestTemplateBuilder().requestFactory(SimpleClientHttpRequestFactory::new), mapper, 3000, 15000);
        Function<String, Optional<List<DailyKline>>> live = code -> {
            System.out.println("取数 " + code);
            try {
                Thread.sleep(REQUEST_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return provider.fetchDailyKlines(code, BARS_TO_FETCH);
        };
        KlineSource source = new CachedKlineSource(Path.of("backtest-data"),
                rawDir.map(dir -> rawResponseFetcher(dir, mapper)).orElse(live), refresh, mapper);

        RunSummary summary = run(new StockCatalog().all(), source, today);
        // 默认按数据截止日命名：同一份数据重跑覆盖同一报告
        LocalDate named = summary.dataThrough() != null ? summary.dataThrough() : today;
        Path out = requestedOut.orElse(Path.of("..", "docs", "回测", "技术分回测-" + named + ".md"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, BacktestReport.render(summary));
        System.out.println("判定：" + summary.verdict().label() + "；参与 " + summary.coverage().size()
                + " 只，排除 " + summary.excluded().size() + " 只；报告：" + out.toAbsolutePath().normalize());
    }
}
