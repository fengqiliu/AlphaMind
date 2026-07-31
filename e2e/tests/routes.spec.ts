import { test, expect } from "@playwright/test";

test.describe("应用路由", () => {
  test("历史记录页面应该可访问", async ({ page }) => {
    await page.goto("/history");
    await expect(
      page.getByRole("heading", { name: "历史记录" }),
    ).toBeVisible();
  });

  test("自选股页面应该可访问", async ({ page }) => {
    await page.goto("/watchlist");
    await expect(
      page.getByRole("heading", { name: "自选股" }),
    ).toBeVisible();
  });
});
