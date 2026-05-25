import { test, expect } from "@playwright/test";

const WEEKLY_MOCK = { code: 200, message: "success", data: [] };

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(WEEKLY_MOCK),
    }),
  );
});

test.describe("页面加载", () => {
  test("应该显示正确的页面标题", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/AlphaMind/);
  });

  test("主内容区域应该可见", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("main")).toBeVisible();
  });
});

test.describe("侧边栏导航", () => {
  test("应该显示 AlphaMind Logo", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("aside")).toBeVisible();
    await expect(page.getByText("AlphaMind")).toBeVisible();
  });

  test("应该显示所有导航链接", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("link", { name: /智能分析/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /AI对话/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /自选股/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /历史记录/ })).toBeVisible();
  });
});

test.describe("股票搜索框", () => {
  test("应该显示搜索输入框", async ({ page }) => {
    await page.goto("/");
    await expect(
      page.getByPlaceholder("搜索股票代码或名称..."),
    ).toBeVisible();
  });

  test("可以在搜索框中输入文字", async ({ page }) => {
    await page.goto("/");
    const input = page.getByPlaceholder("搜索股票代码或名称...");
    await input.fill("贵州茅台");
    await expect(input).toHaveValue("贵州茅台");
  });
});

test.describe("策略和模式选择器", () => {
  test("策略选择器应包含三个选项", async ({ page }) => {
    await page.goto("/");
    const strategySelect = page.locator("select").first();
    await expect(strategySelect).toBeVisible();
    await expect(strategySelect.locator("option")).toHaveCount(3);
  });

  test("策略选择器应包含保守、平衡、激进策略", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("🎯 保守策略")).toBeVisible();
    await expect(page.getByText("⚖️ 平衡策略")).toBeVisible();
    await expect(page.getByText("🚀 激进策略")).toBeVisible();
  });

  test("分析模式选择器应包含流水线和辩论模式", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("🔄 流水线模式")).toBeVisible();
    await expect(page.getByText("⚔️ 辩论模式")).toBeVisible();
  });

  test("默认策略为平衡策略", async ({ page }) => {
    await page.goto("/");
    const strategySelect = page.locator("select").first();
    await expect(strategySelect).toHaveValue("balanced");
  });

  test("默认模式为流水线模式", async ({ page }) => {
    await page.goto("/");
    const modeSelect = page.locator("select").nth(1);
    await expect(modeSelect).toHaveValue("pipeline");
  });
});

test.describe("开始分析按钮", () => {
  test("未选择股票时开始分析按钮应被禁用", async ({ page }) => {
    await page.goto("/");
    const startBtn = page.getByRole("button", { name: /开始分析/ });
    await expect(startBtn).toBeDisabled();
  });
});

test.describe("每周精选版块", () => {
  test("每周精选区域应该存在", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText(/每周精选|本周推荐|WEEKLY/i)).toBeVisible({
      timeout: 5000,
    });
  });
});
