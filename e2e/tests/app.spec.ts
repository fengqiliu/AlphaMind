import { test, expect } from "@playwright/test";

test.describe("AlphaMind 首页", () => {
  test("应该显示页面标题", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/AlphaMind/);
  });
});

test.describe("股票搜索", () => {
  test("搜索框应该可以输入", async ({ page }) => {
    await page.goto("/");
    const searchInput = page.getByPlaceholder(/搜索/);
    await expect(searchInput).toBeVisible();
  });
});

test.describe("分析页面", () => {
  test("应该能访问分析页面", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("main")).toBeVisible();
  });
});
