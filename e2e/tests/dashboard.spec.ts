import { test, expect } from "@playwright/test";

test.describe("监控和风控面板", () => {
  test("监控面板应该可见", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByText(/监控/)).toBeVisible({ timeout: 10000 });
  });

  test("风险指标应该显示", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByText(/风险/)).toBeVisible({ timeout: 10000 });
  });
});
