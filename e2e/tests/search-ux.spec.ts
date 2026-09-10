import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", route => route.fulfill({ json: { code: 200, data: [] } }));
  await page.route("**/stocks/search*", route => route.fulfill({ json: { code: 200, data: [
    { code: "600519", name: "贵州茅台", industry: "白酒" },
    { code: "000858", name: "五粮液", industry: "白酒" },
  ] } }));
  await page.goto("/");
});

test("搜索下拉可以真实点击，不被图表遮挡", async ({ page }) => {
  await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
  await page.getByRole("button", { name: /贵州茅台.*600519/ }).click();
  await expect(page.getByRole("button", { name: "开始分析" })).toBeEnabled();
  await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
});

test("键盘选择、Escape 关闭和清空搜索", async ({ page }) => {
  const input = page.getByPlaceholder("搜索股票代码或名称...");
  await input.fill("酒");
  await expect(page.getByRole("button", { name: /五粮液.*000858/ })).toBeVisible();
  await input.press("ArrowDown");
  await input.press("ArrowDown");
  await input.press("Enter");
  await expect(page.getByText("五粮液", { exact: true })).toBeVisible();
  await expect(input).toHaveValue("");
  await input.fill("茅台");
  await expect(page.getByRole("button", { name: /贵州茅台.*600519/ })).toBeVisible();
  await input.press("Escape");
  await expect(page.getByRole("button", { name: /贵州茅台.*600519/ })).toHaveCount(0);
  await page.getByRole("button", { name: "清空搜索" }).click();
  await expect(input).toHaveValue("");
});

test("网络失败显示错误而非空搜索结果", async ({ page }) => {
  await page.route("**/stocks/search*", route => route.fulfill({ status: 503, json: {} }));
  await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
  await expect(page.getByRole("alert").filter({ hasText: "搜索失败" })).toBeVisible();
  await expect(page.getByText("未找到相关股票")).toHaveCount(0);
});
