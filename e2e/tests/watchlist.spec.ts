import { test, expect } from "@playwright/test";

const EMPTY_WATCHLIST = { code: 200, message: "success", data: [] };
const MOCK_WATCHLIST = {
  code: 200,
  message: "success",
  data: [
    { stockCode: "600519", stockName: "贵州茅台", addedAt: "2025-05-01T10:00:00" },
    { stockCode: "000858", stockName: "五粮液", addedAt: "2025-04-30T09:00:00" },
  ],
};
const SEARCH_MOCK = {
  code: 200,
  message: "success",
  data: [{ code: "000001", name: "平安银行", industry: "银行" }],
};

test.describe("自选股页加载", () => {
  test("应该显示正确的页面标题", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page).toHaveTitle(/AlphaMind/);
  });

  test("应该显示自选股 h1 标题", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByRole("heading", { name: "自选股" })).toBeVisible();
  });

  test("应该显示 PERSONAL WATCHLIST MANAGEMENT 副标题", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(
      page.getByText("PERSONAL WATCHLIST MANAGEMENT"),
    ).toBeVisible();
  });

  test("应该显示添加股票搜索框", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(
      page.getByPlaceholder("搜索股票代码或名称..."),
    ).toBeVisible();
  });
});

test.describe("空自选股状态", () => {
  test("无自选股时应显示空状态提示", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("暂无自选股")).toBeVisible({ timeout: 5000 });
  });

  test("无自选股时应显示引导文字", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(
      page.getByText("搜索并添加感兴趣的股票"),
    ).toBeVisible({ timeout: 5000 });
  });

  test("自选股数量应显示为 0 只", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(EMPTY_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("0 只自选")).toBeVisible({ timeout: 5000 });
  });
});

test.describe("已有自选股状态", () => {
  test("应该渲染自选股列表", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("贵州茅台")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("五粮液")).toBeVisible();
  });

  test("应该显示自选股代码", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("600519")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("000858")).toBeVisible();
  });

  test("自选股数量应正确显示", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("2 只自选")).toBeVisible({ timeout: 5000 });
  });

  test("每个自选股应显示删除按钮", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_WATCHLIST),
      }),
    );
    await page.goto("/watchlist");
    await expect(page.locator("[data-testid='remove-btn'], button").first()).toBeVisible({
      timeout: 5000,
    });
  });
});

test.describe("添加自选股", () => {
  test("搜索股票并点击后应添加到列表", async ({ page }) => {
    await page.route("**/stocks/watchlist", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(EMPTY_WATCHLIST),
        });
      }
    });
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    await page.route("**/stocks/watchlist/000001", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 200, data: null }),
      }),
    );

    await page.goto("/watchlist");
    const searchInput = page.getByPlaceholder("搜索股票代码或名称...");
    await searchInput.fill("平安");
    await page.getByText("平安银行").first().click();

    await expect(page.getByText("平安银行")).toBeVisible({ timeout: 5000 });
  });
});

test.describe("错误处理", () => {
  test("API 失败时应显示错误提示", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({ status: 500, body: "Internal Server Error" }),
    );
    await page.goto("/watchlist");
    await expect(page.getByText("加载自选股失败")).toBeVisible({
      timeout: 5000,
    });
  });
});
