import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.route("**/stocks/watchlist", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.route("**/analysis/history*", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
});

for (const width of [390, 768, 1440]) {
  test(`${width}px 工作台与导航无横向溢出`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    await expect(
      page.getByRole("combobox", { name: "投资策略" }),
    ).toBeVisible();
    await expect(
      page.getByRole("combobox", { name: "分析模式" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "开始分析" })).toBeDisabled();
    for (const name of ["智能分析", "AI对话", "自选股", "历史记录"]) {
      await expect(
        page
          .getByRole("navigation", { name: "主导航" })
          .getByRole("link", { name, exact: true }),
      ).toBeVisible();
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await expect(page.getByText("ONLINE", { exact: true })).toHaveCount(0);
    await expect(page.getByText("3,268.45", { exact: true })).toHaveCount(0);
    await page.screenshot({
      path: `../output/playwright/workspace-${width}.png`,
      fullPage: true,
    });
    if (width === 390) {
      await page.getByRole("link", { name: "自选股", exact: true }).click();
      await expect(
        page.getByRole("heading", { name: "自选股", exact: true }),
      ).toBeVisible();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
      await page.screenshot({
        path: "../output/playwright/watchlist-mobile.png",
        fullPage: true,
      });
    }
  });
}

test("每周观察失败后可重试，成功空数据不继续显示错误", async ({ page }) => {
  let attempts = 0;
  await page.route("**/stocks/recommendations/weekly", (route) => {
    attempts++;
    return attempts === 1
      ? route.fulfill({ status: 503, json: {} })
      : route.fulfill({ json: { code: 200, data: [] } });
  });
  await page.goto("/");
  await expect(
    page.getByRole("alert").filter({ hasText: "本周观察加载失败" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "重新加载" }).click();
  await expect(
    page.getByText("暂无可用观察标的", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByText("本周观察加载失败", { exact: false }),
  ).toHaveCount(0);
});
