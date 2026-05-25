import { test, expect } from "@playwright/test";

const WEEKLY_MOCK = { code: 200, message: "success", data: [] };
const HISTORY_MOCK = { code: 200, message: "success", data: [] };
const WATCHLIST_MOCK = { code: 200, message: "success", data: [] };

test.describe("路由导航", () => {
  test("应该能访问首页 /", async ({ page }) => {
    await page.route("**/stocks/recommendations/weekly", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WEEKLY_MOCK),
      }),
    );
    await page.goto("/");
    await expect(page).toHaveURL("/");
    await expect(page).toHaveTitle(/AlphaMind/);
  });

  test("应该能访问历史记录页 /history", async ({ page }) => {
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(HISTORY_MOCK),
      }),
    );
    await page.goto("/history");
    await expect(page).toHaveURL("/history");
  });

  test("应该能访问自选股页 /watchlist", async ({ page }) => {
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WATCHLIST_MOCK),
      }),
    );
    await page.goto("/watchlist");
    await expect(page).toHaveURL("/watchlist");
  });

  test("应该能访问 AI 对话页 /chat", async ({ page }) => {
    await page.goto("/chat");
    await expect(page).toHaveURL("/chat");
  });
});

test.describe("侧边栏点击导航", () => {
  test("点击 AI对话 导航到 /chat", async ({ page }) => {
    await page.route("**/stocks/recommendations/weekly", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WEEKLY_MOCK),
      }),
    );
    await page.goto("/");
    await page.getByRole("link", { name: /AI对话/ }).click();
    await expect(page).toHaveURL("/chat");
  });

  test("点击 自选股 导航到 /watchlist", async ({ page }) => {
    await page.route("**/stocks/recommendations/weekly", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WEEKLY_MOCK),
      }),
    );
    await page.route("**/stocks/watchlist", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WATCHLIST_MOCK),
      }),
    );
    await page.goto("/");
    await page.getByRole("link", { name: /自选股/ }).click();
    await expect(page).toHaveURL("/watchlist");
  });

  test("点击 历史记录 导航到 /history", async ({ page }) => {
    await page.route("**/stocks/recommendations/weekly", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WEEKLY_MOCK),
      }),
    );
    await page.route("**/analysis/history*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(HISTORY_MOCK),
      }),
    );
    await page.goto("/");
    await page.getByRole("link", { name: /历史记录/ }).click();
    await expect(page).toHaveURL("/history");
  });

  test("点击 智能分析 从其他页面回到 /", async ({ page }) => {
    await page.route("**/stocks/recommendations/weekly", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(WEEKLY_MOCK),
      }),
    );
    await page.goto("/chat");
    await page.getByRole("link", { name: /智能分析/ }).click();
    await expect(page).toHaveURL("/");
  });
});
