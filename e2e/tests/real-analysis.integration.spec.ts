import { test, expect } from "@playwright/test";

const integrationEnabled = process.env.ALPHAMIND_E2E_INTEGRATION === "1";
const backendOrigin = process.env.ALPHAMIND_BACKEND_ORIGIN ?? "http://localhost:8080";

test.describe("真实前后端分析链路", () => {
  test.skip(
    !integrationEnabled,
    "设置 ALPHAMIND_E2E_INTEGRATION=1 后运行真实后端集成测试",
  );
  test.setTimeout(180_000);

  test("搜索股票 → 发起 SSE → 收齐完整报告 → 历史记录可查询", async ({
    page,
    request,
  }) => {
    const healthResponse = await request.get(`${backendOrigin}/actuator/health`);
    const healthPayload = await healthResponse.json();
    // Redis 是可选依赖；健康端点可能因 Redis DOWN 返回 503，但 PostgreSQL 必须可用。
    expect(healthPayload.components?.db?.status).toBe("UP");

    await page.goto("/");

    await page.getByPlaceholder("搜索股票代码或名称...").fill("600519");
    await expect(page.getByRole("button", { name: /贵州茅台.*600519/ })).toBeVisible();
    const search = await request.get(
      `${backendOrigin}/api/v1/stocks/search?query=600519`,
    );
    expect(search.ok()).toBeTruthy();
    expect((await search.json()).data).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: "600519", name: "贵州茅台" }),
      ]),
    );

    await page
      .getByRole("button", { name: /贵州茅台.*600519/ })
      .click();
    const startButton = page.getByRole("button", { name: "开始分析" });
    await expect(startButton).toBeEnabled();

    const streamResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/api/v1/analysis/stream") &&
        response.request().method() === "GET",
    );
    await startButton.click();
    const stream = await streamResponse;
    expect(stream.ok()).toBeTruthy();
    expect(stream.headers()["content-type"]).toContain("text/event-stream");

    // 这些字段由 result 事件一次性填充，能够区分完整报告和仅收到阶段 data。
    await expect(page.getByText("分析结果", { exact: true })).toBeVisible({
      timeout: 150_000,
    });
    await expect(page.getByText("入场价", { exact: true })).toBeVisible();
    await expect(page.getByText("建议持仓周期", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "开始分析" })).toBeEnabled();

    await page.getByRole("link", { name: /历史记录/ }).click();
    await expect(page.getByText("贵州茅台", { exact: true }).first()).toBeVisible({
      timeout: 30_000,
    });
    const history = await request.get(
      `${backendOrigin}/api/v1/analysis/history?stockCode=600519&limit=50`,
    );
    expect(history.ok()).toBeTruthy();
    const historyPayload = await history.json();
    expect(historyPayload.data).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ stockCode: "600519", stockName: "贵州茅台" }),
      ]),
    );
    await expect(page.getByText("贵州茅台", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("600519", { exact: true }).first()).toBeVisible();
  });
});
