"use client";

import { useState } from "react";
import { Calculator, Loader2 } from "lucide-react";
import { Button } from "@/components/common/Button";
import { computeRiskBudget } from "@/api/portfolio";
import type { PersonalAsset, PersonalModelReview } from "@/types/investment";
import {
  DecisionSelect,
  PercentInput,
  inputStyle,
  labelStyle,
  type ReviewSetter,
} from "./formControls";

type DecisionKey = "weeklyTiming" | "bottomDivergence" | "volatilityContraction" | "noLeverage";
type PercentKey =
  | "projectedPositionPct"
  | "projectedThemePct"
  | "cashAfterTradePct"
  | "portfolioDrawdownPct"
  | "goldPositionLimitPct";

/** 择时确认（Gate 3/4）与交易后组合风险预算（Gate 5）。 */
export function RiskBudgetSection({
  review,
  set,
  asset,
}: {
  review: PersonalModelReview;
  set: ReviewSetter;
  asset: PersonalAsset | undefined;
}) {
  const decision = (key: DecisionKey, label: string) => (
    <DecisionSelect key={key} label={label} value={review[key]} onChange={(value) => set(key, value)} />
  );
  const percent = (key: PercentKey, label: string) => (
    <PercentInput key={key} label={label} value={review[key]} onChange={(value) => set(key, value)} />
  );
  const [plannedBuy, setPlannedBuy] = useState("");
  const [computing, setComputing] = useState(false);
  const [budgetMessage, setBudgetMessage] = useState<string | null>(null);
  const basis = review.portfolioBasis;
  const fillFromPortfolio = async () => {
    if (!asset) return;
    setComputing(true);
    setBudgetMessage(null);
    try {
      const budget = await computeRiskBudget(asset.code, Number(plannedBuy) || 0);
      if (budget.totalValue == null) {
        set("portfolioBasis", null);
        setBudgetMessage(`未自动填写：${budget.problems.join("；")}`);
        return;
      }
      set("projectedPositionPct", budget.projectedPositionPct);
      set("projectedThemePct", budget.projectedThemePct);
      set("cashAfterTradePct", budget.cashAfterTradePct);
      if (budget.portfolioDrawdownPct != null)
        set("portfolioDrawdownPct", budget.portfolioDrawdownPct);
      set("portfolioBasis", budget.complete ? budget : null);
      setBudgetMessage(
        budget.problems.length ? `已填写可推算的项；${budget.problems.join("；")}` : null,
      );
    } catch (failure) {
      setBudgetMessage(failure instanceof Error ? failure.message : "组合推算失败");
    } finally {
      setComputing(false);
    }
  };
  return (
    <details className="glass-card p-5 sm:p-6">
      <summary className="cursor-pointer text-base font-semibold">择时确认与交易后风险预算</summary>
      <p className="mt-4 mb-5 text-xs leading-6 text-[var(--text-muted)]">
        日 RSI14 与 ATR20 来自真实日 K；周 RSI
        使用已结束周，数据不足则留空。周线、底背离和波动收敛需对应最新交易日人工复核。权重按整个组合计算，主题应合并相关持仓。
      </p>
      <div className="mb-5 rounded-xl bg-[var(--accent-subtle)] p-4">
        <div className="flex flex-wrap items-end gap-3">
          <label className={`${labelStyle} w-48`}>
            本次拟买入金额（元）
            <input
              aria-label="本次拟买入金额"
              type="number"
              min="0"
              value={plannedBuy}
              onChange={(event) => setPlannedBuy(event.target.value)}
              className={inputStyle}
              placeholder="0"
            />
          </label>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!asset || computing}
            onClick={() => void fillFromPortfolio()}
          >
            {computing ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Calculator className="size-3.5" />
            )}
            按持仓推算
          </Button>
        </div>
        <p className="mt-2 text-xs leading-6 text-[var(--text-muted)]">
          用“我的持仓”和实时价推算下方四项；拟买入金额从现金与低波动资产中扣除。任一持仓只有过期行情时不自动填写。填写后仍可手改，报告会注明是否改过。
        </p>
        {basis && (
          <p role="status" className="mt-2 text-xs leading-6 text-[var(--text-secondary)]">
            已按 {new Date(basis.computedAt).toLocaleString("zh-CN")} 的持仓推算：总资产{" "}
            {basis.totalValue?.toLocaleString("zh-CN")} 元，拟买入{" "}
            {basis.plannedBuyAmount.toLocaleString("zh-CN")} 元，{basis.valuations.length} 条持仓。
          </p>
        )}
        {budgetMessage && (
          <p role="status" className="mt-2 text-xs leading-6 text-[var(--bearish)]">
            {budgetMessage}
          </p>
        )}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {decision("weeklyTiming", "当期日线与周线联合复核")}
        {decision("bottomDivergence", "已确认超卖后底背离")}
        {decision("volatilityContraction", "已确认波动收敛")}
        {decision("noLeverage", "确认不使用杠杆")}
        {percent("projectedPositionPct", "交易后单标的权重（%）")}
        {percent("projectedThemePct", `交易后${asset?.theme ?? "主题"}合计权重（%）`)}
        {percent("cashAfterTradePct", "交易后现金及低波动比例（%）")}
        {percent("portfolioDrawdownPct", "当前组合回撤幅度（%）")}
        {asset?.category === "黄金矿股ETF" &&
          percent("goldPositionLimitPct", "黄金矿股ETF人工仓位上限（%）")}
      </div>
      <p className="mt-5 text-xs leading-6 text-[var(--text-muted)]">
        执行采用原文范围的保守端：普通单股 5%、成长科技起步
        3%、主题合计 20%、现金底线 15%、组合回撤 20%。10%–20%
        的试仓比例指单标的目标仓位内部，不是总资产比例。
      </p>
    </details>
  );
}
