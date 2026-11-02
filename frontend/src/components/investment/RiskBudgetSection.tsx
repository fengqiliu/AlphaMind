import type { PersonalAsset, PersonalModelReview } from "@/types/investment";
import { DecisionSelect, PercentInput, type ReviewSetter } from "./formControls";

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
  return (
    <details className="glass-card p-5 sm:p-6">
      <summary className="cursor-pointer text-base font-semibold">择时确认与交易后风险预算</summary>
      <p className="mt-4 mb-5 text-xs leading-6 text-[var(--text-muted)]">
        日 RSI14 与 ATR20 来自真实日 K；周 RSI
        使用已结束周，数据不足则留空。周线、底背离和波动收敛需对应最新交易日人工复核。权重按整个组合计算，主题应合并相关持仓。
      </p>
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
