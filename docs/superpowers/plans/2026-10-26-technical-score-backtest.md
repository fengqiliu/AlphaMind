# 技术分回测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用与线上相同的技术评分代码逐日回放 72 只股票约 5 年日 K，检验技术分对未来 5/10/20 日超额收益的区分力，并输出可复现的 Markdown 报告。

**Architecture:** 先把 `TechnicalAgent` 的计算抽成纯函数 `TechnicalScoring`（线上与回测唯一口径）。`com.alphamind.backtest` 包内分为数据源（可注入）、回放引擎（产生样本）、统计（分组/IC/t 值）、报告渲染四个文件，入口为不启动 Spring 的 `main`。

**Tech Stack:** Java 17、Jackson、JUnit 5；数据复用 `EastmoneyKlineMarketDataProvider`。

**Spec:** `docs/superpowers/specs/2026-10-26-technical-score-backtest-design.md`

## Global Constraints

- 线上行为不变：`TechnicalAgent` 输出（分数、分项、解释、指标值）逐字段与重构前一致。
- 每日评分窗口 = 截至 t 的最近 **120** 根；现价 = t 日收盘；不足 120 根的日期跳过。
- 入场 t+1 开盘，出场 t+h 收盘，h ∈ {5, 10, 20}。
- 成本：买入 0.025% + 0.05%；卖出 0.025% + 0.05% + 印花税 0.05%。
- 涨停不可买：t+1 开=高=低=收 且 相对 t 收盘涨幅 ≥ 9.5%（代码以 300/301/688 开头为 19.5%）。
- 分档：≥70 / [40,70) / <40；细分表 10 分一档。样本内/外按日期 70%/30%。
- 判定（样本外、h=10）：超额差(≥70 − <40) > 0 且 IC 均值 > 0 且不重叠 t > 2 → “有区分力”；满足其一 → “弱/不确定”；都不满足 → “未见区分力”。
- `mvn test` 必须保持离线；回测只通过 `main` 手动运行。报告文字为中文，注明“历史统计，不构成投资建议”。

## Review Focus

- 停牌导致日期不连续：h 日按“该股之后第 h 根 K 线”计，而非日历日；不足 h 根的尾部样本丢弃 → Task 3 测试 `tailWithoutFullHoldingIsDropped`。
- 某日全池只有 1 只股票有样本：横截面 IC 无法计算，应跳过该日而非产生 NaN → Task 4 测试 `icSkipsDatesWithFewerThanFiveStocks`。
- 取数失败或根数不足 120+h 的股票：跳过并在报告“数据”表中列为排除，而不是中断整个回测 → Task 5 测试 `failedSymbolsAreListedNotFatal`。
- 某档样本为 0：表格显示“—”，判定按“不满足”处理，不能除零 → Task 4 测试 `emptyBucketYieldsNullStats`。
- 分数并列（大量 50 分）：Spearman 用平均秩 → Task 4 测试 `spearmanUsesAverageRanksForTies`。

---

### Task 1: 抽出 `TechnicalScoring` 纯函数

**Files:**

- Create: `backend/src/main/java/com/alphamind/scoring/TechnicalScoring.java`
- Modify: `backend/src/main/java/com/alphamind/agent/TechnicalAgent.java:160-314`（移除 `calculateTechnicalIndicators`/`ema`/`rsi`/`kdj`/`bollinger`/`r2`/`r3`，改为调用新类）
- Test: `backend/src/test/java/com/alphamind/scoring/TechnicalScoringTest.java`

**Interfaces:**

- Produces: `public static TechnicalIndicatorsDTO TechnicalScoring.score(List<double[]> klines, double price)`，bar 布局 `[open, close, low, high]`；无效输入抛 `IllegalArgumentException`，消息与现有一致。

- [ ] **Step 1:** 写 `TechnicalScoringTest`：`flatSeriesScoresFifty`（40 根同价 → 分数 50、RSI12=50、MACD 柱 0）；`matchesAgentOutputOnFixture`（固定 60 根正弦+趋势序列，`TechnicalAgent` 经 `analyze` 得到的 `technicalScore`/`scoreContributions`/`rsi`/`macd` 与 `TechnicalScoring.score` 相等）；`rejectsShortSeries`（33 根抛异常）。
- [ ] **Step 2:** 运行 `mvn -Dtest=TechnicalScoringTest test`，预期编译失败。
- [ ] **Step 3:** 原样迁移计算代码到 `TechnicalScoring`（private static 辅助方法），`TechnicalAgent` 改调用 `TechnicalScoring.score(marketData.getKlines(), marketData.getCurrentPrice())`（price 为 null 时仍抛“缺少有效报价”）。
- [ ] **Step 4:** 运行全部测试（见 CLAUDE/会话中的 JAVA_HOME + mockito javaagent 命令），预期全绿，`TechnicalAgentTest` 未改动即通过。
- [ ] **Step 5:** 提交 `refactor: extract TechnicalScoring pure function`。

### Task 2: 回测数据源与缓存

**Files:**

- Create: `backend/src/main/java/com/alphamind/backtest/KlineSource.java`
- Create: `backend/src/main/java/com/alphamind/backtest/CachedKlineSource.java`
- Modify: `.gitignore`（加 `backend/backtest-data/`）
- Test: `backend/src/test/java/com/alphamind/backtest/CachedKlineSourceTest.java`

**Interfaces:**

- Produces: `interface KlineSource { Optional<List<DailyKline>> load(String code); }`
- Produces: `CachedKlineSource(Path dir, Function<String, Optional<List<DailyKline>>> fetcher, boolean refresh, ObjectMapper mapper)`：命中缓存文件 `<dir>/<code>.json` 且 `refresh=false` 时不调用 fetcher；否则调用 fetcher，成功则写缓存。

- [ ] **Step 1:** 测试 `readsCacheWithoutFetching`、`fetchesAndWritesWhenMissing`、`refreshIgnoresCache`、`fetchFailureReturnsEmptyAndWritesNothing`（用 `@TempDir` 与计数 fetcher）。
- [ ] **Step 2:** 运行确认失败。
- [ ] **Step 3:** 实现；JSON 为 `DailyKline` 数组（Jackson 支持 record，需注册 `JavaTimeModule`）。
- [ ] **Step 4:** 运行通过。
- [ ] **Step 5:** 提交 `feat(backtest): cached kline source`。

### Task 3: 回放引擎

**Files:**

- Create: `backend/src/main/java/com/alphamind/backtest/BacktestSample.java`
- Create: `backend/src/main/java/com/alphamind/backtest/TechnicalReplay.java`
- Test: `backend/src/test/java/com/alphamind/backtest/TechnicalReplayTest.java`

**Interfaces:**

- Consumes: `TechnicalScoring.score`
- Produces: `record BacktestSample(String code, LocalDate signalDate, int score, int horizon, double grossReturn, double netReturn)`
- Produces: `record ReplayResult(List<BacktestSample> samples, int limitUpSkipped)`；`static ReplayResult TechnicalReplay.replay(String code, List<DailyKline> bars, int window, int[] horizons, Costs costs)`
- Produces: `record Costs(double buy, double sell)`，`Costs.DEFAULT = new Costs(0.00075, 0.00125)`；`netReturn = exit*(1-sell)/(entry*(1+buy)) - 1`。

- [ ] **Step 1:** 测试：
  - `noLookahead`：同一前 150 根，后续分别接暴涨/暴跌序列，第 150 根日期的 `score` 相同。
  - `entryIsNextOpenExitIsCloseAfterH`：手工序列验证 h=5 的 `grossReturn == close[t+5]/open[t+1]-1`。
  - `limitUpNextDaySkipped`：t+1 一字涨停 10%（主板）被跳过且 `limitUpSkipped==1`；同幅度对 `300xxx` 不跳过（阈值 19.5%）。
  - `costsApplied`：毛收益 0 时 `netReturn ≈ -0.002`（误差 1e-9）。
  - `tailWithoutFullHoldingIsDropped`：最后 h 个信号日无该 h 样本。
  - `firstSignalNeedsFullWindow`：首个样本 `signalDate == bars.get(window-1).tradeDate()`。
- [ ] **Step 2:** 运行确认失败。
- [ ] **Step 3:** 实现：对 t ∈ [window-1, n-2]，取 `bars[t-window+1..t]` 转 `[open,close,low,high]`，`score(window, close[t])`；对每个 h 若 `t+h < n` 生成样本。
- [ ] **Step 4:** 运行通过。
- [ ] **Step 5:** 提交 `feat(backtest): no-lookahead technical replay`。

### Task 4: 统计

**Files:**

- Create: `backend/src/main/java/com/alphamind/backtest/BacktestStats.java`
- Test: `backend/src/test/java/com/alphamind/backtest/BacktestStatsTest.java`

**Interfaces:**

- Consumes: `BacktestSample`
- Produces:
  - `record ExcessSample(BacktestSample sample, double excess)`；`static List<ExcessSample> withExcess(List<BacktestSample>)`（excess = netReturn − 同 signalDate 同 horizon 全池 netReturn 均值）。
  - `record BucketStats(String label, int count, Double meanNet, Double medianNet, Double winRate, Double meanExcess)`；`static List<BucketStats> buckets(List<ExcessSample>, int horizon, List<int[]> ranges)`，空档字段为 null。
  - `static double spearman(double[] x, double[] y)`（平均秩）。
  - `record IcStats(int dates, Double meanIc, Double positiveShare, Double tNonOverlap)`；`static IcStats ic(List<BacktestSample>, int horizon)`：每日 ≥5 只才计算；t 值取第 0, h, 2h… 个 IC 计算 `mean/(sd/sqrt(k))`。
  - `static LocalDate splitDate(List<BacktestSample>, double inSampleShare)`：按去重排序后的信号日第 70% 分位。
  - `enum Verdict { DISCRIMINATIVE, WEAK, NONE }`；`static Verdict verdict(List<BucketStats> h10OutOfSample, IcStats h10OutOfSampleIc)`。

- [ ] **Step 1:** 测试 `excessSubtractsSameDateMean`、`bucketsSplitAtSeventyAndForty`、`emptyBucketYieldsNullStats`、`spearmanUsesAverageRanksForTies`（`[1,1,2]` vs `[1,2,3]` ≈ 0.866）、`icSkipsDatesWithFewerThanFiveStocks`、`tStatUsesEveryHthDate`、`verdictRequiresBothConditions`（三种组合各一）。
- [ ] **Step 2:** 运行确认失败。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 运行通过。
- [ ] **Step 5:** 提交 `feat(backtest): bucket, IC and verdict statistics`。

### Task 5: 运行入口与报告

**Files:**

- Create: `backend/src/main/java/com/alphamind/backtest/BacktestReport.java`
- Create: `backend/src/main/java/com/alphamind/backtest/TechnicalBacktestMain.java`
- Test: `backend/src/test/java/com/alphamind/backtest/BacktestReportTest.java`

**Interfaces:**

- Consumes: Task 2–4 全部。
- Produces: `static String BacktestReport.render(RunSummary)`；`record RunSummary(LocalDate runDate, List<SymbolCoverage> coverage, List<String> excluded, Map<Integer, List<BucketStats>> inSample, Map<Integer, List<BucketStats>> outSample, Map<Integer, List<BucketStats>> fineOutSample, Map<Integer, IcStats> icIn, Map<Integer, IcStats> icOut, LocalDate split, int limitUpSkipped, Verdict verdict)`；`record SymbolCoverage(String code, String name, LocalDate from, LocalDate to, int bars)`。
- Produces: `static RunSummary TechnicalBacktestMain.run(Collection<StockCatalog.StockMetadata> universe, KlineSource source, LocalDate runDate)`；`main(args)`：参数 `--refresh`、`--out=<path>`，默认输出 `docs/回测/技术分回测-<runDate>.md`；数据源 = `CachedKlineSource(backend/backtest-data, code -> provider.fetchDailyKlines(code, 1250), …)`，provider 用公开构造器 `(new RestTemplateBuilder(), new ObjectMapper(), 3000, 15000)`，请求间 sleep 300ms。

- [ ] **Step 1:** 测试 `failedSymbolsAreListedNotFatal`（内存 source：1 只返回 empty、1 只仅 100 根、3 只合成 400 根 → excluded 含前两只，run 正常返回）；`reportContainsVerdictMethodAndDisclaimer`（渲染含“判定”“120 根”“0.2%”“历史统计，不构成投资建议”“幸存者偏差”，空档显示“—”）。
- [ ] **Step 2:** 运行确认失败。
- [ ] **Step 3:** 实现；股票数少于 5 时 IC 全为空、判定为 NONE 并在报告说明。
- [ ] **Step 4:** 运行全部测试通过（仍离线）。
- [ ] **Step 5:** 提交 `feat(backtest): runnable technical backtest with markdown report`。

### Task 6: 真实运行与文档

**Files:**

- Create: `docs/回测/技术分回测-<runDate>.md`（程序生成）
- Modify: `docs/技术评分与规则一致度.md`（“未经回测”处改为引用报告与判定）、`changelog.md`、`CLAUDE.md`（Build 段加回测运行命令）

- [ ] **Step 1:** 运行：
  ```bash
  cd backend && mvn -q compile dependency:build-classpath -Dmdep.outputFile=$TMPDIR/cp.txt
  java -cp target/classes:$(cat $TMPDIR/cp.txt) com.alphamind.backtest.TechnicalBacktestMain --out=../docs/回测/技术分回测-$(date +%F).md
  ```
  需要网络 `push2his.eastmoney.com`。预期：报告生成，排除列表合理（ETF/新股可能不足）。
- [ ] **Step 2:** 人工核对报告：样本数量级、各档样本 > 0、判定与表格一致；结果如实写入，不论好坏。
- [ ] **Step 3:** 更新三份文档，提交 `docs: technical score backtest report`。
