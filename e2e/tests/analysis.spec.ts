import { test, expect } from "@playwright/test";

const WEEKLY_MOCK = { code: 200, message: "success", data: [] };
const SEARCH_MOCK = {
  code: 200,
  message: "success",
  data: [
    { code: "600519", name: "贵州茅台", industry: "白酒" },
    { code: "000858", name: "五粮液", industry: "白酒" },
  ],
};

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(WEEKLY_MOCK),
    }),
  );
});

test.describe("股票搜索与选择", () => {
  test("输入关键字后应显示搜索结果下拉列表", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );

    await page.goto("/");
    const input = page.getByPlaceholder("搜索股票代码或名称...");
    await input.fill("茅台");

    await expect(page.getByText("贵州茅台")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("600519")).toBeVisible();
  });

  test("搜索结果中应显示多支股票", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );

    await page.goto("/");
    await page.getByPlaceholder("搜索股票代码或名称...").fill("酒");

    await expect(page.getByText("贵州茅台")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("五粮液")).toBeVisible();
  });

  test("选择股票后开始分析按钮应被启用", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );

    await page.goto("/");
    await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
    await page.getByText("贵州茅台").first().click();

    const startBtn = page.getByRole("button", { name: /开始分析/ });
    await expect(startBtn).toBeEnabled({ timeout: 3000 });
  });

  test("无结果时应显示提示信息", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: [] }),
      }),
    );

    await page.goto("/");
    await page.getByPlaceholder("搜索股票代码或名称...").fill("不存在的股票");

    await expect(page.getByText("未找到相关股票")).toBeVisible({
      timeout: 5000,
    });
  });
});

test.describe("策略切换", () => {
  test("应该能切换到保守策略", async ({ page }) => {
    await page.goto("/");
    const strategySelect = page.locator("select").first();
    await strategySelect.selectOption("conservative");
    await expect(strategySelect).toHaveValue("conservative");
  });

  test("应该能切换到激进策略", async ({ page }) => {
    await page.goto("/");
    const strategySelect = page.locator("select").first();
    await strategySelect.selectOption("aggressive");
    await expect(strategySelect).toHaveValue("aggressive");
  });

  test("应该能切换到辩论模式", async ({ page }) => {
    await page.goto("/");
    const modeSelect = page.locator("select").nth(1);
    await modeSelect.selectOption("debate");
    await expect(modeSelect).toHaveValue("debate");
  });
});

test.describe("分析流程触发", () => {
  test("点击开始分析后应发起 SSE 请求", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    await page.route("**/analysis/stream*", (route) =>
      route.fulfill({
        status: 200,
        headers: {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache",
        },
        body: "event: complete\ndata: {}\n\n",
      }),
    );

    await page.goto("/");
    await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
    await page.getByText("贵州茅台").first().click();

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.url().includes("/analysis/stream")),
      page.getByRole("button", { name: /开始分析/ }).click(),
    ]);

    expect(request.url()).toContain("stockCode=600519");
  });

  test("开始分析后开始分析按钮应变为停止分析", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    // Keep SSE connection open to maintain "analyzing" state
    await page.route("**/analysis/stream*", (route) =>
      route.fulfill({
        status: 200,
        headers: {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache",
        },
        body: "event: stage\ndata: {\"stage\":\"MARKET\"}\n\n",
      }),
    );

    await page.goto("/");
    await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
    await page.getByText("贵州茅台").first().click();
    await page.getByRole("button", { name: /开始分析/ }).click();

    await expect(
      page.getByRole("button", { name: /停止分析/ }),
    ).toBeVisible({ timeout: 5000 });
  });
});
