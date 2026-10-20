import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";

const enabled = process.env.ALPHAMIND_E2E_INTEGRATION === "1";
const backend = process.env.ALPHAMIND_BACKEND_ORIGIN ?? "http://localhost:8080";

test.describe("真实报告持久化与详情", () => {
  test.skip(
    !enabled,
    "设置 ALPHAMIND_E2E_INTEGRATION=1 后运行真实数据库与行情验收",
  );
  test.setTimeout(180_000);

  test("两种模式完整往返、历史详情刷新与导出一致", async ({
    page,
    request,
  }) => {
    for (const [strategy, mode] of [
      ["AGGRESSIVE", "PIPELINE"],
      ["CONSERVATIVE", "DEBATE"],
    ]) {
      const response = await request.post(
        `${backend}/api/v1/analysis/analyze`,
        {
          data: { stockCode: "600519", strategy, mode },
          timeout: 150_000,
        },
      );
      const envelope = await response.json();
      expect(envelope.code, envelope.message).toBe(200);
      const original = envelope.data;
      expect(original.strategy).toBe(strategy);
      expect(original.mode).toBe(mode);
      // 当前报价源不提供估值；缺失不能被解释为亏损，评分来源随JSONB往返。
      expect(original.sentimentData.valuationStatus).toBe("MISSING");
      expect(original.sentimentData.mediaAttention ?? null).toBeNull();
      expect(original.sentimentData.scoreExplanation).toContain("可用信号归一化");
      expect(original.sentimentData.dataGaps).toContain("PE缺失或为零，未参与估值评分，不代表亏损");
      const restored = await request.get(
        `${backend}/api/v1/analysis/${original.id}`,
      );
      const restoredBody = await restored.json();
      // 比较整个报告，而非只检查返回 200 或挑选少数字段。
      expect(restoredBody.code).toBe(200);
      expect(restoredBody.data).toEqual(original);
      const recent = await request.get(
        `${backend}/api/v1/analysis/history?stockCode=600519&limit=50`,
      );
      expect(
        (await recent.json()).data.find(
          (item: { id: string }) => item.id === original.id,
        ),
      ).toEqual(original);
      if (mode === "DEBATE") expect(original.debateViews).toHaveLength(3);
      else expect(original.debateViews).toBeUndefined();

      await page.goto(`/history?report=${original.id}`);
      await page.reload();
      const detail = page.getByRole("region", {
        name: "报告详情",
        exact: true,
      });
      await expect(
        detail.getByText(mode === "DEBATE" ? "辩论模式" : "流水线模式", {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        detail.getByText(
          strategy === "CONSERVATIVE" ? "保守策略" : "激进策略",
          { exact: true },
        ),
      ).toBeVisible();
      const download = page.waitForEvent("download");
      await detail.getByRole("button", { name: "导出 JSON" }).click();
      const file = await (await download).path();
      expect(JSON.parse(await readFile(file!, "utf8"))).toEqual(original);
      if (mode === "DEBATE") {
        for (const text of [
          "多头分析师 · 看多",
          "空头分析师 · 看空",
          "中立分析师 · 中立",
        ]) {
          await expect(detail.getByText(text, { exact: true })).toBeVisible();
        }
        await page.screenshot({
          path: "../output/playwright/history-real-debate-desktop.png",
          fullPage: true,
        });
        await page.setViewportSize({ width: 390, height: 900 });
        await expect
          .poll(() =>
            page.evaluate(
              () => document.documentElement.scrollWidth <= innerWidth,
            ),
          )
          .toBe(true);
        await page.screenshot({
          path: "../output/playwright/history-real-debate-mobile.png",
          fullPage: true,
        });
      }
    }
  });

  test("V1 旧记录迁移后可读且不推测执行配置", async ({ request }) => {
    // 仅在专门准备了 V1 兼容样本的环境验证；普通真实环境无需此样本。
    test.skip(
      process.env.ALPHAMIND_E2E_LEGACY_FIXTURE !== "1",
      "需要 V1 升级样本",
    );
    const response = await request.get(
      `${backend}/api/v1/analysis/legacy-report-v1`,
    );
    const payload = await response.json();
    expect(payload.code).toBe(200);
    expect(payload.data.stockName).toBe("旧版兼容样本");
    expect(payload.data.tradeSignal.entryPrice).toBe(100);
    expect(payload.data.strategy).toBeUndefined();
    expect(payload.data.mode).toBeUndefined();
    expect(payload.data.debateViews).toBeUndefined();
  });
});
