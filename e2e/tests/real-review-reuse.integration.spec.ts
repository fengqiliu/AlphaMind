import { test, expect } from "@playwright/test";

test.describe("真实证据复用", () => {
  test.skip(
    process.env.ALPHAMIND_E2E_INTEGRATION !== "1",
    "需要真实行情与独立 PostgreSQL",
  );
  test.setTimeout(180000);
  test("跳过较新的空复核和通用分析，重载后新报告保留来源，旧报告不变", async ({
    page,
    request,
  }) => {
    const backend =
      process.env.ALPHAMIND_BACKEND_ORIGIN ?? "http://localhost:8080";
    const blank = await (
      await request.post(`${backend}/api/v1/investment-model/analyze`, {
        data: { stockCode: "002352" },
        timeout: 150000,
      })
    ).json();
    expect(blank.code, blank.message).toBe(200);
    const today = blank.data.personalModel.assessedOn;
    const oldDate = new Date(`${today}T12:00:00Z`);
    oldDate.setUTCDate(oldDate.getUTCDate() - 2);
    const later = new Date(`${today}T12:00:00Z`);
    later.setUTCDate(later.getUTCDate() + 30);
    const evidence = {
      reviewedAt: oldDate.toISOString().slice(0, 10),
      validUntil: later.toISOString().slice(0, 10),
      evidenceSource: "E2E TEST ONLY 财报来源，非真实财务结论",
      counterArgument: "测试样本不构成投资建议",
      valuation: "OVERVALUED",
      earnings: "PASS",
      cashFlow: "PASS",
      businessLogic: "PASS",
      valueTrapClear: "PASS",
      weeklyTiming: "PASS",
      bottomDivergence: "PASS",
      volatilityContraction: "PASS",
      noLeverage: "PASS",
      projectedPositionPct: 2,
      projectedThemePct: 10,
      cashAfterTradePct: 20,
      portfolioDrawdownPct: 5,
    };
    const sourceEnvelope = await (
      await request.post(`${backend}/api/v1/investment-model/analyze`, {
        data: { stockCode: "002352", review: evidence },
        timeout: 150000,
      })
    ).json();
    expect(sourceEnvelope.code, sourceEnvelope.message).toBe(200);
    const source = sourceEnvelope.data;
    expect(source.personalModel.entryCandidate).toBe(false);
    const generic = await (
      await request.post(`${backend}/api/v1/analysis/analyze`, {
        data: { stockCode: "002352", strategy: "AGGRESSIVE", mode: "PIPELINE" },
        timeout: 150000,
      })
    ).json();
    expect(generic.code, generic.message).toBe(200);
    const whitespace = await (
      await request.post(`${backend}/api/v1/investment-model/analyze`, {
        data: { stockCode: "002352", review: { evidenceSource: " \n\t　 " } },
        timeout: 150000,
      })
    ).json();
    expect(whitespace.code, whitespace.message).toBe(200);
    const latest = await (
      await request.get(
        `${backend}/api/v1/investment-model/reviews/002352/latest`,
      )
    ).json();
    expect(latest.code).toBe(200);
    expect(latest.data.sourceReportId).toBe(source.id);
    expect(latest.data.review.valuation).toBe("UNKNOWN");
    expect(latest.data.review.projectedPositionPct).toBeUndefined();
    expect(latest.data.review.weeklyTiming).toBe("UNKNOWN");
    expect(latest.data.review.validUntil).toBe(evidence.validUntil);
    await page.goto("/investment-model?stock=002352");
    await page.getByRole("button", { name: "载入上次复核" }).click();
    await expect(
      page.getByRole("textbox", { name: "证据来源与报告期" }),
    ).toHaveValue(evidence.evidenceSource);
    await expect(page.getByRole("combobox", { name: "估值结论" })).toHaveValue(
      "UNKNOWN",
    );
    const submitted = page.waitForResponse(
      (response) =>
        response.url().endsWith("/investment-model/analyze") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
    const newBody = await (await submitted).json();
    expect(newBody.code, newBody.message).toBe(200);
    expect(newBody.data.personalModel.evidence.sourceReportId).toBe(source.id);
    expect(newBody.data.personalModel.entryCandidate).toBe(false);
    expect(
      newBody.data.personalModel.evidence.projectedPositionPct,
    ).toBeUndefined();
    const restored = await (
      await request.get(`${backend}/api/v1/analysis/${newBody.data.id}`)
    ).json();
    expect(restored.data).toEqual(newBody.data);
    const unchanged = await (
      await request.get(`${backend}/api/v1/analysis/${source.id}`)
    ).json();
    expect(unchanged.data).toEqual(source);
    const invalid = await (
      await request.post(`${backend}/api/v1/investment-model/analyze`, {
        data: { stockCode: "603068", review: { sourceReportId: source.id } },
        timeout: 150000,
      })
    ).json();
    expect(invalid.code).toBe(400);
    expect(invalid.message).toContain("不匹配");
    await page.getByRole("link", { name: "查看已保存的报告与导出" }).click();
    const detail = page.getByRole("region", { name: "报告详情", exact: true });
    await detail.getByText("查看证据快照与规则边界", { exact: true }).click();
    await expect(detail.getByRole("link", { name: source.id })).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({
      path: "../output/playwright/review-reuse-real.png",
      fullPage: true,
    });
  });
});
