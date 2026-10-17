import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/stocks/recommendations/weekly", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.route("**/stocks/search*", (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: [
          { code: "600519", name: "贵州茅台", industry: "白酒" },
          { code: "000858", name: "五粮液", industry: "白酒" },
        ],
      },
    }),
  );
  // 可控连接将 complete 和 result 分开投递，避免同一网络包掩盖提前关闭。
  await page.addInitScript(() => {
    class ControlledSource extends EventTarget {
      closed = false;
      constructor(public url: string) {
        super();
        const sources =
          (window as unknown as { analysisSources?: ControlledSource[] })
            .analysisSources ?? [];
        sources.push(this);
        Object.assign(window, {
          analysisSource: this,
          analysisSources: sources,
        });
      }
      close() {
        this.closed = true;
      }
      emit(event: string, data: unknown) {
        if (!this.closed)
          this.dispatchEvent(
            new MessageEvent(event, { data: JSON.stringify(data) }),
          );
      }
    }
    Object.assign(window, { EventSource: ControlledSource });
  });
  await page.goto("/");
  await page.getByPlaceholder("搜索股票代码或名称...").fill("茅台");
  await page.getByText("贵州茅台", { exact: true }).click();
  await page.getByRole("button", { name: /开始分析/ }).click();
});

type SourceWindow = Window & {
  analysisSource: {
    closed: boolean;
    url: string;
    emit: (event: string, data: unknown) => void;
  };
};

for (const earlyComplete of [true, false]) {
  test(`保留股票并等待完整结果：${earlyComplete ? "complete → result" : "result → complete"}`, async ({
    page,
  }) => {
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /停止分析/ })).toBeVisible();
    const url = await page.evaluate(
      () => (window as unknown as SourceWindow).analysisSource.url,
    );
    expect(new URL(url, "http://localhost").searchParams.get("stockCode")).toBe(
      "600519",
    );
    if (earlyComplete) {
      await page.evaluate(() =>
        (window as unknown as SourceWindow).analysisSource.emit("complete", {}),
      );
      await expect(
        page.getByRole("button", { name: /停止分析/ }),
      ).toBeVisible();
      expect(
        await page.evaluate(
          () => (window as unknown as SourceWindow).analysisSource.closed,
        ),
      ).toBe(false);
    }
    await page.evaluate(() =>
      (window as unknown as SourceWindow).analysisSource.emit("result", {
        code: 200,
        data: {
          id: "regression-report",
          createdAt: "2026-09-10T12:00:00",
          tradeSignal: {
            type: "HOLD",
            entryPrice: 123.45,
            targetPrice: 130,
            stopLoss: 110,
            holdingPeriodDays: 30,
            rationale: "完整结果已接收",
          },
        },
      }),
    );
    await expect(
      page.getByText("完整结果已接收", { exact: false }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /开始分析/ })).toBeEnabled();
    expect(
      await page.evaluate(
        () => (window as unknown as SourceWindow).analysisSource.closed,
      ),
    ).toBe(true);
    await page.evaluate(() =>
      (window as unknown as SourceWindow).analysisSource.emit("complete", {}),
    );
    await page.getByRole("button", { name: /开始分析/ }).click();
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(
      page.getByText("完整结果已接收", { exact: false }),
    ).toHaveCount(0);
    await page.getByRole("button", { name: /停止分析/ }).click();
    await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /开始分析/ })).toBeEnabled();
  });
}

test("保存失败的业务错误保留原文，不会被传输错误覆盖", async ({ page }) => {
  await page.evaluate(() =>
    (window as unknown as SourceWindow).analysisSource.emit("error", {
      code: 500,
      message: "分析报告保存失败，请稍后重试",
    }),
  );
  await expect(
    page.getByRole("alert").filter({ hasText: "分析报告保存失败，请稍后重试" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "开始分析" })).toBeEnabled();
  expect(
    await page.evaluate(
      () => (window as unknown as SourceWindow).analysisSource.closed,
    ),
  ).toBe(true);
  await expect(
    page.getByText("连接分析服务失败", { exact: false }),
  ).toHaveCount(0);
});

test("阶段建议不会提前作为已完成报告展示", async ({ page }) => {
  await page.evaluate(() =>
    (window as unknown as SourceWindow).analysisSource.emit("data", {
      agentType: "PORTFOLIO",
      data: {
        type: "HOLD",
        entryPrice: 123,
        targetPrice: 130,
        stopLoss: 110,
        holdingPeriodDays: 30,
        rationale: "尚未保存的阶段建议",
      },
    }),
  );
  await expect(
    page.getByRole("heading", { name: "分析结果", exact: true }),
  ).toHaveCount(0);
  await page.evaluate(() =>
    (window as unknown as SourceWindow).analysisSource.emit("error", {
      code: 500,
      message: "分析报告保存失败，请稍后重试",
    }),
  );
  await expect(
    page.getByRole("heading", { name: "分析结果", exact: true }),
  ).toHaveCount(0);
});

test("不完整报告显示错误并关闭连接", async ({ page }) => {
  await page.evaluate(() =>
    (window as unknown as SourceWindow).analysisSource.emit("result", {
      code: 200,
      data: {},
    }),
  );
  await expect(
    page.getByRole("alert").filter({ hasText: "分析结果不完整" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "开始分析" })).toBeEnabled();
});

test("切换标的关闭旧连接，排队的旧结果不能污染新标的", async ({ page }) => {
  await page.getByPlaceholder("搜索股票代码或名称...").fill("五粮液");
  await page.getByRole("button", { name: /五粮液.*000858/ }).click();
  expect(
    await page.evaluate(
      () => (window as unknown as SourceWindow).analysisSource.closed,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "开始分析" }).click();
  await page.evaluate(() => {
    const old = (window as unknown as { analysisSources: EventTarget[] })
      .analysisSources[0];
    // 模拟 close 前已排队、在新请求开始后才被派发的浏览器事件。
    old.dispatchEvent(
      new MessageEvent("result", {
        data: JSON.stringify({
          code: 200,
          data: {
            id: "old-report",
            stockCode: "600519",
            tradeSignal: { type: "BUY", rationale: "旧标的结果" },
          },
        }),
      }),
    );
  });
  await expect(page.getByText("五粮液", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "停止分析" })).toBeVisible();
  await expect(page.getByText("旧标的结果")).toHaveCount(0);
  await expect(
    page.getByRole("alert").filter({ hasText: "分析结果不完整" }),
  ).toHaveCount(0);
});

test("离开页面关闭连接，返回后可以重新分析", async ({ page }) => {
  await page.route("**/analysis/history*", (route) =>
    route.fulfill({ json: { code: 200, data: [] } }),
  );
  await page.getByRole("link", { name: "历史记录", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "历史记录", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as unknown as SourceWindow).analysisSource.closed,
    ),
  ).toBe(true);
  await page.getByRole("link", { name: "智能分析", exact: true }).click();
  await expect(page.getByRole("button", { name: "开始分析" })).toBeEnabled();
  await expect(page.getByText("贵州茅台", { exact: true })).toBeVisible();
});

test("修改下次分析模式，不会改写已完成报告的研究流程", async ({ page }) => {
  await page.evaluate(() =>
    (window as unknown as SourceWindow).analysisSource.emit("result", {
      code: 200,
      data: {
        id: "pipeline-report",
        createdAt: "2026-10-16T12:00:00",
        tradeSignal: {
          type: "HOLD",
          entryPrice: 100,
          targetPrice: 103,
          stopLoss: 93,
          holdingPeriodDays: 30,
          rationale: "流水线报告",
        },
      },
    }),
  );
  await page.getByRole("combobox", { name: "分析模式" }).selectOption("debate");
  await expect(
    page.getByRole("region", { name: "研究流程" }).getByRole("listitem"),
  ).toHaveCount(4);
  await page.getByRole("button", { name: "开始分析" }).click();
  await expect(
    page.getByRole("region", { name: "研究流程" }).getByRole("listitem"),
  ).toHaveCount(5);
});
