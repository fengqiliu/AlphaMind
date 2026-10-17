import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const model = JSON.parse(
  readFileSync(
    resolve(
      __dirname,
      "../../backend/src/main/resources/investment/personal-model-v1.1.json",
    ),
    "utf8",
  ),
);
const asset = model.assets.find(
  (item: { code: string }) => item.code === "002352",
);
const assessment = {
  modelId: model.id,
  version: model.version,
  versionDate: model.versionDate,
  sourceSha256: model.sourceSha256,
  asset,
  level: "S1",
  action: "仅触发复核，不自动买入",
  entryCandidate: false,
  priceZone: "TIER_2",
  currentPrice: 29.5,
  gates: [
    "价格",
    "估值",
    "盈利与现金流",
    "核心逻辑",
    "RSI / ATR 择时",
    "组合风险预算",
  ].map((label, index) => ({
    id: String(index),
    label,
    status: index === 0 ? "PASS" : "PENDING",
    detail: "测试用复核证据，非真实投资结论",
  })),
  timing: {
    dailyRsi14: 38,
    weeklyRsi14: null,
    atr20: 0.8,
    atrPricePct: 2.71,
    trueRange: 1,
    nextBatchPriceCeiling: 28,
    dataAsOf: "2026-10-16",
  },
  missingEvidence: ["估值进入合理/低位的人工确认", "交易后单标的权重"],
  blockers: [],
  notes: [
    "价格区间只触发复核",
    "不使用文档旧持仓推测当前账户，不生成固定止损价或收益目标",
  ],
  evidence: {},
  assessedOn: "2026-10-16",
};
const report = {
  id: "personal-fixture-report",
  stockCode: asset.code,
  stockName: asset.name,
  finalSignal: "HOLD",
  createdAt: "2026-10-16T12:00:00",
  personalModel: assessment,
  tradeSignal: { type: "HOLD", entryPrice: 29.5, rationale: assessment.action },
  marketData: {
    stockCode: asset.code,
    stockName: asset.name,
    currentPrice: 29.5,
    change: 0,
    changePercent: 0,
    open: 29.5,
    high: 30,
    low: 29,
    volume: 10000,
    amount: 295000,
    turnoverRate: 1,
    pe: null,
    pb: null,
    marketCap: null,
    updateTime: "2026-10-16T12:00:00",
    dataSource: "TEST_FIXTURE",
    dataStatus: "FRESH_CACHE",
    stale: false,
  },
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/investment-model", (route) =>
    route.fulfill({ json: { code: 200, data: model } }),
  );
  await page.route("**/api/v1/investment-model/analyze", (route) =>
    route.fulfill({ json: { code: 200, data: report } }),
  );
  await page.route("**/analysis/history*", (route) =>
    route.fulfill({ json: { code: 200, data: [report] } }),
  );
  await page.route("**/analysis/personal-fixture-report", (route) =>
    route.fulfill({ json: { code: 200, data: report } }),
  );
});

test("13 个观察标的、三档价格与博通暂定专项条件来自原文", async ({ page }) => {
  await page.goto("/investment-model");
  const select = page.getByRole("combobox", { name: "观察标的" });
  await expect(select.locator("option")).toHaveCount(13);
  await select.selectOption("603068");
  await expect(page.getByText("36–40", { exact: true })).toBeVisible();
  await expect(page.getByText("31–34", { exact: true })).toBeVisible();
  await expect(page.getByText("26–29", { exact: true })).toBeVisible();
  await expect(page.getByText("· 暂定观察区")).toBeVisible();
  for (const label of Object.values(
    model.assets.find((a: { code: string }) => a.code === "603068")
      .specialChecks,
  )) {
    await expect(
      page.getByRole("combobox", { name: label as string, exact: true }),
    ).toHaveValue("UNKNOWN");
  }
});

test("缺证据提交为 S1，历史与导出不会生成固定目标价和置信度", async ({
  page,
}) => {
  await page.goto("/investment-model?stock=002352");
  await expect(page.getByRole("combobox", { name: "观察标的" })).toHaveValue(
    "002352",
  );
  const sent = page.waitForRequest((request) =>
    request.url().endsWith("/investment-model/analyze"),
  );
  await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
  const request = (await sent).postDataJSON();
  expect(request.review.projectedPositionPct).toBeUndefined();
  expect(request.review.valuation).toBeUndefined();
  await expect(page.getByText("S1", { exact: true })).toBeVisible();
  await expect(
    page.getByText("仅触发复核，不自动买入", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "查看已保存的报告与导出" }).click();
  const detail = page.getByRole("region", { name: "报告详情", exact: true });
  await expect(detail.getByText("S1", { exact: true })).toBeVisible();
  await expect(detail.getByText("目标价", { exact: true })).toHaveCount(0);
  await expect(detail.getByText("止损价", { exact: true })).toHaveCount(0);
  await expect(detail.getByText("65%", { exact: true })).toHaveCount(0);
  const download = page.waitForEvent("download");
  await detail.getByRole("button", { name: "导出 Markdown" }).click();
  const content = await readFile((await (await download).path())!, "utf8");
  expect(content).toContain("S1");
  expect(content).toContain("不自动买入");
  expect(content).not.toContain("目标价：");
  await page.reload();
  await expect(detail.getByText("S1", { exact: true })).toBeVisible();
});

test("手工复核与交易后风险值随请求提交，切换标的清除旧证据", async ({
  page,
}) => {
  await page.goto("/investment-model?stock=002352");
  await page
    .getByRole("combobox", { name: "估值结论" })
    .selectOption("UNDERVALUED");
  await page
    .getByRole("textbox", { name: "证据来源与报告期" })
    .fill("测试财报来源及估值分位，不是真实投资结论");
  await page
    .getByRole("textbox", { name: "为什么现在不应该买" })
    .fill("测试反方风险");
  await page.getByText("择时确认与交易后风险预算", { exact: true }).click();
  await page
    .getByRole("spinbutton", { name: "交易后单标的权重（%）" })
    .fill("2.5");
  await page
    .getByRole("spinbutton", { name: "交易后物流合计权重（%）" })
    .fill("10");
  const sent = page.waitForRequest((request) =>
    request.url().endsWith("/investment-model/analyze"),
  );
  await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
  const data = (await sent).postDataJSON();
  expect(data.review.projectedPositionPct).toBe(2.5);
  expect(data.review.projectedThemePct).toBe(10);
  expect(data.review.valuation).toBe("UNDERVALUED");
  await page.getByRole("combobox", { name: "观察标的" }).selectOption("603068");
  await expect(
    page.getByRole("textbox", { name: "证据来源与报告期" }),
  ).toHaveValue("");
  await expect(page.getByText("S1", { exact: true })).toHaveCount(0);
});

for (const width of [390, 1440]) {
  test(`${width}px 投资模型表单与结果无横向溢出`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/investment-model?stock=002352");
    await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
    await expect(page.getByText("S1", { exact: true })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({
      path: `../output/playwright/personal-model-${width}.png`,
      fullPage: true,
    });
  });
}

test("模型读取失败可重试，保存失败不显示成功报告", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/v1/investment-model", (route) =>
    ++attempts === 1
      ? route.fulfill({ status: 503, json: {} })
      : route.fulfill({ json: { code: 200, data: model } }),
  );
  await page.goto("/investment-model");
  await expect(
    page.getByRole("alert").filter({ hasText: "投资模型加载失败" }),
  ).toContainText("加载失败");
  await page.getByRole("button", { name: "重新加载" }).click();
  await expect(page.getByRole("combobox", { name: "观察标的" })).toBeVisible();
  await page.route("**/api/v1/investment-model/analyze", (route) =>
    route.fulfill({
      json: { code: 500, message: "个人模型报告保存失败，请稍后重试" },
    }),
  );
  await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: "保存失败" }),
  ).toContainText("保存失败");
  await expect(
    page.getByRole("link", { name: "查看已保存的报告与导出" }),
  ).toHaveCount(0);
});

test("模型外标的不会静默替换为另一只股票", async ({ page }) => {
  await page.goto("/investment-model?stock=600519");
  await expect(
    page.getByRole("alert").filter({ hasText: "不在本模型" }),
  ).toBeVisible();
  await expect(page.getByRole("combobox", { name: "观察标的" })).toHaveValue(
    "",
  );
  await expect(
    page.getByRole("button", { name: "按最新行情复核并保存" }),
  ).toBeDisabled();
});
