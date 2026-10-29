package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.service.StockCatalog.StockMetadata;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BacktestReportTest {
    private static final LocalDate RUN = LocalDate.of(2026, 10, 26);

    private static List<DailyKline> synthetic(int n, double phase) {
        List<DailyKline> bars = new ArrayList<>();
        double prev = 20;
        for (int i = 0; i < n; i++) {
            double close = 20 + 3 * Math.sin(i / 5.0 + phase) + i * 0.01;
            bars.add(new DailyKline(LocalDate.of(2024, 1, 1).plusDays(i), prev, close,
                    Math.min(prev, close) * 0.99, Math.max(prev, close) * 1.01, 1000, 1000 * close, 1));
            prev = close;
        }
        return bars;
    }

    @Test
    void failedSymbolsAreListedNotFatal() {
        Map<String, List<DailyKline>> data = new HashMap<>();
        data.put("000002", synthetic(100, 0));
        for (int i = 0; i < 3; i++) data.put("60000" + (i + 3), synthetic(400, i));
        KlineSource source = code -> Optional.ofNullable(data.get(code));
        List<StockMetadata> universe = List.of(
                new StockMetadata("000001", "取数失败", "测试", "深圳主板"),
                new StockMetadata("000002", "根数不足", "测试", "深圳主板"),
                new StockMetadata("600003", "甲", "测试", "上海主板"),
                new StockMetadata("600004", "乙", "测试", "上海主板"),
                new StockMetadata("600005", "丙", "测试", "上海主板"));

        RunSummary summary = TechnicalBacktestMain.run(universe, source, RUN);

        assertEquals(3, summary.coverage().size());
        assertEquals(2, summary.excluded().size());
        assertTrue(summary.excluded().get(0).contains("000001"));
        assertTrue(summary.excluded().get(1).contains("000002"));
        assertEquals(Verdict.INSUFFICIENT_DATA, summary.verdict(), "少于 5 只股票时不做判定");
        assertEquals(0, summary.outSample().get(10).stream().mapToInt(BucketStats::count).sum(),
                "每个交易日只有 3 只股票，不足 5 只的样本不计入统计");
        assertNull(summary.split());
    }

    @Test
    void inSampleLeavesHDateGapBeforeSplit() {
        Map<String, List<DailyKline>> data = new HashMap<>();
        List<StockMetadata> universe = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            data.put("60001" + i, synthetic(400, i));
            universe.add(new StockMetadata("60001" + i, "股" + i, "测试", "上海主板"));
        }
        data.put("517400", synthetic(400, 9));
        universe.add(new StockMetadata("517400", "黄金股ETF", "ETF", "上海ETF"));
        RunSummary summary = TechnicalBacktestMain.run(universe, code -> Optional.ofNullable(data.get(code)), RUN);
        int inH5 = summary.inSample().get(5).stream().mapToInt(BucketStats::count).sum();
        int inH20 = summary.inSample().get(20).stream().mapToInt(BucketStats::count).sum();
        assertEquals(6 * (20 - 5), inH5 - inH20, "样本内按持有期留出 h 个信号日的间隔");
        assertTrue(summary.dataNotes().stream().anyMatch(n -> n.contains("ETF") && n.contains("517400")));
        assertTrue(BacktestReport.render(summary).contains("517400 黄金股ETF"));
    }

    @Test
    void allSymbolsExcludedStillProducesReport() {
        RunSummary summary = TechnicalBacktestMain.run(
                List.of(new StockMetadata("000001", "取数失败", "测试", "深圳主板")), code -> Optional.empty(), RUN);
        assertEquals(1, summary.excluded().size());
        assertNull(summary.split());
        assertEquals(Verdict.INSUFFICIENT_DATA, summary.verdict());
        String markdown = BacktestReport.render(summary);
        assertTrue(markdown.contains("**判定：数据不足，不做判定**"));
    }

    @Test
    void reportContainsVerdictMethodAndDisclaimer() {
        var empty = new BucketStats("≥70", 0, null, null, null, null);
        var low = new BucketStats("<40", 12, -0.01, -0.012, 0.4, -0.004);
        Map<Integer, List<BucketStats>> buckets = Map.of(10, List.of(empty, low));
        Map<Integer, IcStats> ic = Map.of(10, new IcStats(30, 0.031, 0.55, 1.4));
        var summary = new RunSummary(RUN,
                List.of(new SymbolCoverage("600036", "招商银行", LocalDate.of(2021, 10, 26), RUN, 1250)),
                List.of("517400 黄金ETF：取数失败"), buckets, buckets, buckets, ic, ic,
                List.of("600000 浦发银行：刷新失败，沿用旧缓存"), LocalDate.of(2025, 4, 1), 3, Verdict.WEAK);

        String markdown = BacktestReport.render(summary);

        for (String expected : List.of("判定", "弱/不确定", "120 根", "0.2%", "历史统计，不构成投资建议", "幸存者偏差",
                "517400 黄金ETF：取数失败", "沿用旧缓存", "数据截至 2026-10-26", "2025-04-01", "招商银行", "0.031", "-0.40%")) {
            assertTrue(markdown.contains(expected), "缺少: " + expected);
        }
        assertTrue(markdown.contains("| ≥70 | 0 | — | — | — | — |"), "空档显示为 —");
        assertEquals(LocalDate.of(2026, 10, 26), summary.dataThrough());
    }
}
