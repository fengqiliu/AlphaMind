import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.route("**/stocks/search*", (route) =>
    route.fulfill({ json: { code: 200, data: [
      { code: "600519", name: "贵州茅台", industry: "白酒" },
    ] } }),
  );
  // 可控连接将 complete 和 result 分开投递，避免同一网络包掩盖提前关闭。
  await page.addInitScript(() => {
    class ControlledSource extends EventTarget {
      closed = false;
      constructor(public url: string) {
        super();
        Object.assign(window, { analysisSource: this });
      }
      close() { this.closed = true; }
      emit(event: string, data: unknown) {
        if (!this.closed) this.dispatchEvent(new MessageEvent(event, { data: JSON.stringify(data) }));
      }
    }
    Object.assign(window, { EventSource: ControlledSource });
  });
  await page.goto("/");
  await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
  await page.getByText("贵州茅台", { exact: true }).click();
  await page.getByRole("button", { name: /开始分析/ }).click();
});

type SourceWindow = Window & { analysisSource: {
  closed: boolean; url: string; emit: (event: string, data: unknown) => void;
} };

for (const earlyComplete of [true, false]) {
  test(`保留股票并等待完整结果：${earlyComplete ? "complete → result" : "result → complete"}`, async ({ page }) => {
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /停止分析/ })).toBeVisible();
    const url = await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.url);
    expect(new URL(url, "http://localhost").searchParams.get("stockCode")).toBe("600519");
    if (earlyComplete) {
      await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.emit("complete", {}));
      await expect(page.getByRole("button", { name: /停止分析/ })).toBeVisible();
      expect(await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.closed)).toBe(false);
    }
    await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.emit("result", {
      code: 200, data: { id: "regression-report", createdAt: "2026-09-10T12:00:00",
        tradeSignal: { type: "HOLD", entryPrice: 123.45, targetPrice: 130, stopLoss: 110,
          holdingPeriodDays: 30, rationale: "完整结果已接收" },
      },
    }));
    await expect(page.getByText("完整结果已接收", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: /开始分析/ })).toBeEnabled();
    expect(await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.closed)).toBe(true);
    await page.evaluate(() => (window as unknown as SourceWindow).analysisSource.emit("complete", {}));
    await page.getByRole("button", { name: /开始分析/ }).click();
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(page.getByText("完整结果已接收", { exact: false })).toHaveCount(0);
    await page.getByRole("button", { name: /停止分析/ }).click();
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /开始分析/ })).toBeEnabled();
  });
}
