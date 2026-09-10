import { test, expect } from "@playwright/test";

const SEARCH_MOCK = {
  code: 200,
  message: "success",
  data: [{ code: "600519", name: "贵州茅台", industry: "白酒" }],
};
const SESSION_MOCK = {
  code: 200,
  message: "success",
  data: "test-session-abc123",
};

test.describe("AI 对话页加载", () => {
  test("应该显示正确的页面标题", async ({ page }) => {
    await page.goto("/chat");
    await expect(page).toHaveTitle(/AlphaMind/);
  });

  test("应该显示 AI对话 标题文字", async ({ page }) => {
    await page.goto("/chat");
    await expect(page.getByText("AI对话")).toBeVisible();
  });

  test("应该显示 MULTI-AGENT CONVERSATION INTERFACE 副标题", async ({
    page,
  }) => {
    await page.goto("/chat");
    await expect(
      page.getByText("MULTI-AGENT CONVERSATION INTERFACE"),
    ).toBeVisible();
  });
});

test.describe("未选择股票状态", () => {
  test("应该显示股票选择引导区", async ({ page }) => {
    await page.goto("/chat");
    await expect(page.getByText("选择要分析的股票")).toBeVisible();
  });

  test("应该显示带有专用占位符的搜索框", async ({ page }) => {
    await page.goto("/chat");
    await expect(
      page.getByPlaceholder("搜索股票代码或名称，开启AI对话..."),
    ).toBeVisible();
  });

  test("应该显示开启 AI 对话的提示文字", async ({ page }) => {
    await page.goto("/chat");
    await expect(page.getByText("开启与AI分析师的实时对话")).toBeVisible();
  });
});

test.describe("选择股票后的聊天界面", () => {
  test("选择股票后应显示 AI分析师 标题", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    await page.route("**/chat/session*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SESSION_MOCK),
      }),
    );

    await page.goto("/chat");
    const input = page.getByPlaceholder("搜索股票代码或名称，开启AI对话...");
    await input.fill("茅台");
    await page.getByText("贵州茅台").first().click();

    await expect(page.getByText("AI分析师")).toBeVisible({ timeout: 5000 });
  });

  test("选择股票后应隐藏股票搜索引导区", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    await page.route("**/chat/session*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SESSION_MOCK),
      }),
    );

    await page.goto("/chat");
    await page
      .getByPlaceholder("搜索股票代码或名称，开启AI对话...")
      .fill("茅台");
    await page.getByText("贵州茅台").first().click();

    await expect(
      page.getByPlaceholder("搜索股票代码或名称，开启AI对话..."),
    ).toBeHidden({ timeout: 3000 });
  });

  test("选择股票后应出现消息输入区域", async ({ page }) => {
    await page.route("**/stocks/search*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SEARCH_MOCK),
      }),
    );
    await page.route("**/chat/session*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SESSION_MOCK),
      }),
    );

    await page.goto("/chat");
    await page
      .getByPlaceholder("搜索股票代码或名称，开启AI对话...")
      .fill("茅台");
    await page.getByText("贵州茅台").first().click();

    await expect(page.getByRole("button", { name: /清空对话/ })).toBeVisible({
      timeout: 5000,
    });
  });
});
