import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";

const signal = {
  type: "HOLD",
  entryPrice: 100,
  targetPrice: 103,
  stopLoss: 95,
  holdingPeriodDays: 45,
  rationale: "已保存交易计划",
};
const confidence = {
  value: 0.75,
  level: "HIGH",
  explanation: "已保存置信度说明",
};
const report = {
  id: "history-debate",
  stockCode: "600519",
  stockName: "测试研究股票",
  strategy: "CONSERVATIVE",
  mode: "DEBATE",
  finalSignal: "HOLD",
  confidence,
  tradeSignal: signal,
  createdAt: "2026-10-16T12:30:00",
  marketData: {
    stockCode: "600519",
    stockName: "测试研究股票",
    currentPrice: 100,
    change: 1,
    changePercent: 1,
    open: 99,
    high: 101,
    low: 98,
    volume: 10000,
    amount: 1000000,
    turnoverRate: 1.2,
    pe: null,
    pb: null,
    marketCap: null,
    updateTime: "2026-10-16T12:30:00",
    dataSource: "TEST_FIXTURE",
    dataStatus: "FRESH_CACHE",
  },
  technicalIndicators: {
    macd: { dif: 1, dea: 0.5, histogram: 1 },
    rsi: { rsi6: 50, rsi12: 52, rsi24: 51 },
    kdj: { k: 50, d: 50, j: 50 },
    bollinger: { upper: 105, middle: 100, lower: 95 },
    technicalScore: 70,
  },
  sentimentData: {
    sentimentScore: 0.6,
    sentimentTrend: "中性",
    positiveFactors: ["测试利好因素"],
    negativeFactors: ["测试风险因素"],
    mediaAttention: 0.5,
    analysisSummary: "已保存舆情摘要",
    recentHeadlines: ["验收用资讯标题"],
    newsDataSource: "测试数据",
  },
  judgment: {
    finalPosition: "NEUTRAL",
    confidence,
    reasoning: "已保存仲裁理由",
    voteBreakdown: { BULLISH: 3, BEARISH: 3, NEUTRAL: 2 },
    riskWarnings: ["验收风险说明"],
    finalSignal: signal,
  },
  debateViews: [
    {
      agentType: "BULL",
      position: "BULLISH",
      view: "已保存多头观点",
      reasons: ["多头支撑依据"],
      keyPoints: ["多头观察点"],
      attackPoints: ["多头反驳依据"],
      confidence,
    },
    {
      agentType: "BEAR",
      position: "BEARISH",
      view: "已保存空头观点",
      reasons: ["空头支撑依据"],
      keyPoints: ["空头观察点"],
      confidence,
    },
    {
      agentType: "NEUTRAL",
      position: "NEUTRAL",
      view: "已保存中立观点",
      reasons: ["中立支撑依据"],
      keyPoints: ["中立观察点"],
      confidence,
    },
  ],
};

test.beforeEach(async ({ page }) => {
  await page.route("**/analysis/history*", (route) =>
    route.fulfill({ json: { code: 200, data: [report] } }),
  );
  await page.route("**/analysis/history-debate", (route) =>
    route.fulfill({ json: { code: 200, data: report } }),
  );
});

test("键盘打开完整报告、刷新恢复选择、展开三方依据", async ({ page }) => {
  await page.goto("/history");
  const row = page.getByRole("button", { name: /查看报告 测试研究股票/ });
  await row.focus();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/report=history-debate/);
  await page.reload();
  const detail = page.getByRole("region", { name: "报告详情", exact: true });
  await expect(detail.getByText("保守策略", { exact: true })).toBeVisible();
  await expect(detail.getByText("辩论模式", { exact: true })).toBeVisible();
  for (const text of [
    "已保存多头观点",
    "已保存空头观点",
    "已保存中立观点",
    "已保存置信度说明",
    "已保存仲裁理由",
  ]) {
    await expect(detail.getByText(text, { exact: true }).first()).toBeVisible();
  }
  await expect(detail.getByRole("listitem").filter({ hasText: "验收用资讯标题" })).toBeVisible();
  await detail.getByText("查看支撑理由与反驳", { exact: true }).first().click();
  await expect(detail.getByText("多头支撑依据")).toBeVisible();
  await expect(detail.getByText("多头反驳依据")).toBeVisible();
  await page.screenshot({
    path: "../output/playwright/history-detail-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 900 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await page.screenshot({
    path: "../output/playwright/history-detail-mobile.png",
    fullPage: true,
  });
});

test("链接中的旧报告不依赖当前最近历史列表", async ({ page }) => {
  await page.route("**/analysis/history*", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.route("**/analysis/history-debate", (route) =>
    route.fulfill({ json: { code: 200, data: report } }),
  );
  await page.goto("/history?report=history-debate");
  await expect(
    page.getByRole("region", { name: "报告详情" }).getByText("已保存多头观点"),
  ).toBeVisible();
  await expect(page.getByText("暂无分析记录", { exact: true })).toBeVisible();
});

test("JSON 与 Markdown 导出保留执行配置和三方观点", async ({ page }) => {
  await page.goto("/history?report=history-debate");
  const detail = page.getByRole("region", { name: "报告详情" });
  const jsonDownload = page.waitForEvent("download");
  await detail.getByRole("button", { name: "导出 JSON" }).click();
  const jsonFile = await (await jsonDownload).path();
  const data = JSON.parse(await readFile(jsonFile!, "utf8"));
  expect(data.strategy).toBe("CONSERVATIVE");
  expect(data.mode).toBe("DEBATE");
  expect(data.debateViews).toEqual(report.debateViews);
  const markdownDownload = page.waitForEvent("download");
  await detail.getByRole("button", { name: "导出 Markdown" }).click();
  const markdown = await readFile(
    (await (await markdownDownload).path())!,
    "utf8",
  );
  for (const text of [
    "保守策略",
    "辩论模式",
    "已保存多头观点",
    "已保存空头观点",
    "已保存中立观点",
    "多头支撑依据",
    "多头反驳依据",
  ])
    expect(markdown).toContain(text);
});

test("旧报告缺失配置和置信度时如实显示，关闭详情清除链接", async ({ page }) => {
  const legacy = {
    ...report,
    strategy: null,
    mode: null,
    confidence: null,
    judgment: null,
    debateViews: null,
  };
  await page.route("**/analysis/history-debate", (route) =>
    route.fulfill({ json: { code: 200, data: legacy } }),
  );
  await page.goto("/history?report=history-debate");
  const detail = page.getByRole("region", { name: "报告详情" });
  await expect(detail.getByText("策略未记录", { exact: true })).toBeVisible();
  await expect(detail.getByText("模式未记录", { exact: true })).toBeVisible();
  await expect(detail.getByText("本次报告未提供置信度")).toBeVisible();
  await expect(
    detail.getByRole("button", { name: "导出 Markdown" }),
  ).toBeDisabled();
  await detail.getByRole("button", { name: "关闭" }).click();
  await expect(detail).toHaveCount(0);
  await expect(page).toHaveURL(/\/history$/);
});

test("失效报告链接显示业务错误，列表仍可筛选", async ({ page }) => {
  await page.route("**/analysis/missing", (route) =>
    route.fulfill({ json: { code: 404, message: "报告不存在" } }),
  );
  await page.goto("/history?report=missing");
  await expect(
    page.getByRole("alert").filter({ hasText: "报告不存在" }),
  ).toBeVisible();
  await page.getByRole("textbox", { name: "筛选历史股票" }).fill("无匹配");
  await expect(page.getByText("未找到匹配的历史记录")).toBeVisible();
});
