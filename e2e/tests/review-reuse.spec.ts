import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const model = JSON.parse(
  readFileSync(
    resolve(
      __dirname,
      "../../backend/src/main/resources/investment/personal-model-v1.1.json",
    ),
    "utf8",
  ),
);
const draft = {
  sourceReportId: "source-report",
  stockCode: "002352",
  stockName: "顺丰控股",
  modelVersion: "1.1",
  sourceCreatedAt: "2026-10-16T12:00:00",
  expired: true,
  review: {
    sourceReportId: "source-report",
    reviewedAt: "2026-10-01",
    validUntil: "2026-10-10",
    evidenceSource: "测试财报与现金流来源",
    counterArgument: "测试反方证据",
    earnings: "PASS",
    cashFlow: "PASS",
    valuation: "UNKNOWN",
    businessLogic: "UNKNOWN",
    valueTrapClear: "UNKNOWN",
    noLeverage: "UNKNOWN",
    weeklyTiming: "UNKNOWN",
    bottomDivergence: "UNKNOWN",
    volatilityContraction: "UNKNOWN",
    specialChecks: {},
  },
  notices: [
    "仅载入财报项、来源与反方文字；原报告没有修改。",
    "估值、业务逻辑、价值陷阱、择时、杠杆与当前组合数据均需重新确认。",
    "复核日期和有效期未延长，过期证据仍不能通过。",
  ],
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/investment-model", (route) =>
    route.fulfill({ json: { code: 200, data: model } }),
  );
  await page.route("**/investment-model/reviews/002352/latest", (route) =>
    route.fulfill({ json: { code: 200, data: draft } }),
  );
});

test("载入财报证据但清空估值、逻辑、择时与当前风险，保留过期日期", async ({
  page,
}) => {
  await page.goto("/investment-model?stock=002352");
  await page
    .getByRole("combobox", { name: "估值结论" })
    .selectOption("UNDERVALUED");
  await page.getByText("择时确认与交易后风险预算", { exact: true }).click();
  await page
    .getByRole("combobox", { name: "当期日线与周线联合复核" })
    .selectOption("PASS");
  await page
    .getByRole("spinbutton", { name: "交易后单标的权重（%）" })
    .fill("4");
  await page.getByRole("button", { name: "载入上次复核" }).click();
  await expect(
    page.getByRole("textbox", { name: "证据来源与报告期" }),
  ).toHaveValue(draft.review.evidenceSource);
  await expect(
    page.getByRole("combobox", { name: "盈利与扣非利润稳定" }),
  ).toHaveValue("PASS");
  await expect(
    page.getByRole("combobox", { name: "经营现金流支持盈利" }),
  ).toHaveValue("PASS");
  await expect(page.getByRole("combobox", { name: "估值结论" })).toHaveValue(
    "UNKNOWN",
  );
  await expect(
    page.getByRole("combobox", { name: "核心业务逻辑仍成立" }),
  ).toHaveValue("UNKNOWN");
  await expect(
    page.getByRole("combobox", { name: "当期日线与周线联合复核" }),
  ).toHaveValue("UNKNOWN");
  await expect(
    page.getByRole("spinbutton", { name: "交易后单标的权重（%）" }),
  ).toHaveValue("");
  await expect(page.getByLabel("复核日期", { exact: true })).toHaveValue(
    "2026-10-01",
  );
  await expect(page.getByLabel("证据有效至", { exact: true })).toHaveValue(
    "2026-10-10",
  );
  await expect(
    page.getByText("源证据已过期或日期不完整", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "查看来源报告" }),
  ).toHaveAttribute("href", "/history?report=source-report");
  const submitted = page.waitForRequest((request) =>
    request.url().endsWith("/investment-model/analyze"),
  );
  await page.route("**/api/v1/investment-model/analyze", (route) =>
    route.fulfill({ json: { code: 500, message: "测试结束，不生成报告" } }),
  );
  await page.getByRole("button", { name: "按最新行情复核并保存" }).click();
  expect((await submitted).postDataJSON().review.sourceReportId).toBe(
    "source-report",
  );
});

for (const code of [404, 409, 503]) {
  test(`历史复核 ${code} 时保留当前人工输入`, async ({ page }) => {
    await page.route("**/investment-model/reviews/002352/latest", (route) =>
      route.fulfill({
        status: code === 503 ? 503 : 200,
        json: {
          code,
          message: code === 409 ? "模型版本不一致" : "没有可复用证据",
        },
      }),
    );
    await page.goto("/investment-model?stock=002352");
    await page
      .getByRole("textbox", { name: "证据来源与报告期" })
      .fill("本次输入必须保留");
    await page.getByRole("button", { name: "载入上次复核" }).click();
    await expect(
      page.getByRole("textbox", { name: "证据来源与报告期" }),
    ).toHaveValue("本次输入必须保留");
    await expect(
      page.getByRole("button", { name: "按最新行情复核并保存" }),
    ).toBeEnabled();
    await expect(page.getByRole("link", { name: "查看来源报告" })).toHaveCount(
      0,
    );
  });
}

test("切换标的清除复用标记和来源字段", async ({ page }) => {
  await page.goto("/investment-model?stock=002352");
  await page.getByRole("button", { name: "载入上次复核" }).click();
  await expect(page.getByRole("link", { name: "查看来源报告" })).toBeVisible();
  await page.getByRole("combobox", { name: "观察标的" }).selectOption("603068");
  await expect(page.getByRole("link", { name: "查看来源报告" })).toHaveCount(0);
  await expect(
    page.getByRole("textbox", { name: "证据来源与报告期" }),
  ).toHaveValue("");
});
