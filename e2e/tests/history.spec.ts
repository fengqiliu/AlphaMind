import { test, expect } from "@playwright/test";

const MOCK_REPORTS = [
  {
    id: "test-report-001",
    stockCode: "600519",
    stockName: "贵州茅台",
    finalSignal: "BUY",
    confidence: { value: 0.85, level: "HIGH" },
    createdAt: "2025-05-01T10:00:00",
    tradeSignal: {
      type: "BUY",
      entryPrice: 1680.0,
      targetPrice: 1800.0,
      stopLoss: 1600.0,
      rationale: "技术面强势，基本面优良",
      holdingPeriodDays: 30,
    },
  },
  {
    id: "test-report-002",
    stockCode: "000858",
    stockName: "五粮液",
    finalSignal: "HOLD",
    confidence: { value: 0.6, level: "MEDIUM" },
    createdAt: "2025-04-30T14:00:00",
    tradeSignal: {
      type: "HOLD",
      entryPrice: 168.0,
      targetPrice: 180.0,
      stopLoss: 160.0,
      rationale: "震荡行情，观望为主",
      holdingPeriodDays: 15,
    },
  },
];

test.describe("历史记录页加载", () => {
  test("应该显示正确的页面标题", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(page).toHaveTitle(/AlphaMind/);
  });

  test("应该显示历史记录 h1 标题", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByRole("heading", { name: "历史记录" })).toBeVisible();
  });

  test("应该显示 ANALYSIS HISTORY & REPORTS 副标题", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(
      page.getByText("ANALYSIS HISTORY & REPORTS"),
    ).toBeVisible();
  });
});

test.describe("空历史记录状态", () => {
  test("无历史记录时应显示空状态提示", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByText("暂无分析记录")).toBeVisible({
      timeout: 5000,
    });
  });

  test("无历史记录时应显示提示开始分析的文字", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(
      page.getByText("开始分析股票后将自动保存历史"),
    ).toBeVisible({ timeout: 5000 });
  });

  test("记录数显示应为 0 条记录", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByText("0 条记录")).toBeVisible({ timeout: 5000 });
  });
});

test.describe("有历史记录状态", () => {
  test("应该渲染报告列表中的股票名称", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: MOCK_REPORTS }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByText("贵州茅台")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("五粮液")).toBeVisible();
  });

  test("应该显示股票代码", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: MOCK_REPORTS }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByText("600519")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("000858")).toBeVisible();
  });

  test("记录数应正确显示", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: MOCK_REPORTS }),
      }),
    );
    await page.goto("/history");
    await expect(page.getByText("2 条记录")).toBeVisible({ timeout: 5000 });
  });
});

test.describe("错误处理", () => {
  test("API 失败时应显示错误提示", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({ status: 500, body: "Internal Server Error" }),
    );
    await page.goto("/history");
    await expect(page.getByText("加载历史记录失败")).toBeVisible({
      timeout: 5000,
    });
  });
});
