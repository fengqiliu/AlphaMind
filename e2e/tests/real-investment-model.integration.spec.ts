import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";

test.describe("真实个人投资模型", () => {
  test.skip(
    process.env.ALPHAMIND_E2E_INTEGRATION !== "1",
    "需要真实行情与独立 PostgreSQL",
  );
  test.setTimeout(180_000);
  test("真实行情复核、数据库重载、历史与导出保持模型一致", async ({
    page,
    request,
  }, testInfo) => {
    const backend =
      process.env.ALPHAMIND_BACKEND_ORIGIN ?? "http://localhost:8080";
    const definition = await (
      await request.get(`${backend}/api/v1/investment-model`)
    ).json();
    expect(definition.code).toBe(200);
    expect(definition.data.assets).toHaveLength(13);
    const response = await request.post(
      `${backend}/api/v1/investment-model/analyze`,
      { data: { stockCode: "002352" }, timeout: 150_000 },
    );
    const body = await response.json();
    expect(body.code, body.message).toBe(200);
    const report = body.data;
    expect(report.personalModel.entryCandidate).toBe(false);
    expect(["S0", "S1"]).toContain(report.personalModel.level);
    expect(report.personalModel.sourceSha256).toBe(
      definition.data.sourceSha256,
    );
    expect(report.personalModel.evidence.projectedPositionPct).toBeUndefined();
    expect(report.tradeSignal.targetPrice).toBeUndefined();
    expect(report.tradeSignal.stopLoss).toBeUndefined();
    expect(report.confidence).toBeUndefined();
    const restored = await (
      await request.get(`${backend}/api/v1/analysis/${report.id}`)
    ).json();
    expect(restored.data).toEqual(report);
    await page.goto(`/history?report=${report.id}`);
    await page.reload();
    const detail = page.getByRole("region", { name: "报告详情", exact: true });
    await expect(
      detail.getByRole("region", { name: "个人投资模型复核" }),
    ).toBeVisible();
    await expect(
      detail.getByText(report.personalModel.level, { exact: true }),
    ).toBeVisible();
    await expect(detail.getByText("目标价", { exact: true })).toHaveCount(0);
    const jsonDownload = page.waitForEvent("download");
    await detail.getByRole("button", { name: "导出 JSON" }).click();
    expect(
      JSON.parse(await readFile((await (await jsonDownload).path())!, "utf8")),
    ).toEqual(report);
    const mdDownload = page.waitForEvent("download");
    await detail.getByRole("button", { name: "导出 Markdown" }).click();
    const markdown = await readFile((await (await mdDownload).path())!, "utf8");
    expect(markdown).toContain(report.personalModel.sourceSha256);
    expect(markdown).toContain(report.personalModel.action);
    const pdfDownload = page.waitForEvent("download", { timeout: 60000 });
    await detail.getByRole("button", { name: "导出 PDF" }).click();
    const pdf = await pdfDownload;
    const path = testInfo.outputPath("personal-model.pdf");
    await pdf.saveAs(path);
    expect((await readFile(path)).subarray(0, 5).toString()).toBe("%PDF-");
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({
      path: "../output/playwright/personal-model-real-history.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 900 });
    await expect
      .poll(() =>
        page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      )
      .toBe(true);
    await page.screenshot({
      path: "../output/playwright/personal-model-real-mobile.png",
      fullPage: true,
    });
  });
});
