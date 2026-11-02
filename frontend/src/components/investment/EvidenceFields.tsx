import type { Decision, PersonalAsset, PersonalModelReview, Valuation } from "@/types/investment";
import { DecisionSelect, inputStyle, labelStyle, type ReviewSetter } from "./formControls";

/** Gate 1/2 的人工复核字段：日期、估值结论、盈利/现金流/逻辑/价值陷阱、专项检查与文字证据。 */
export function EvidenceFields({
  review,
  set,
  asset,
}: {
  review: PersonalModelReview;
  set: ReviewSetter;
  asset: PersonalAsset | undefined;
}) {
  const decision = (key: "earnings" | "cashFlow" | "businessLogic" | "valueTrapClear", label: string) => (
    <DecisionSelect key={key} label={label} value={review[key]} onChange={(value) => set(key, value)} />
  );
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className={labelStyle}>
          复核日期
          <input
            aria-label="复核日期"
            type="date"
            value={review.reviewedAt ?? ""}
            onChange={(event) => set("reviewedAt", event.target.value || undefined)}
            className={inputStyle}
          />
        </label>
        <label className={labelStyle}>
          证据有效至
          <input
            aria-label="证据有效至"
            type="date"
            value={review.validUntil ?? ""}
            onChange={(event) => set("validUntil", event.target.value || undefined)}
            className={inputStyle}
          />
        </label>
        <label className={labelStyle}>
          估值结论
          <select
            aria-label="估值结论"
            value={review.valuation ?? "UNKNOWN"}
            onChange={(event) => set("valuation", event.target.value as Valuation)}
            className={inputStyle}
          >
            <option value="UNKNOWN">未确认</option>
            <option value="REASONABLE">合理</option>
            <option value="UNDERVALUED">低估</option>
            <option value="OVERVALUED">未通过 / 仍高估</option>
          </select>
        </label>
        {decision("earnings", "盈利与扣非利润稳定")}
        {decision("cashFlow", "经营现金流支持盈利")}
        {decision("businessLogic", "核心业务逻辑仍成立")}
        {decision("valueTrapClear", "已排除价值陷阱与重大风险")}
        {asset &&
          Object.entries(asset.specialChecks).map(([key, label]) => (
            <DecisionSelect
              key={key}
              label={label}
              value={review.specialChecks?.[key]}
              onChange={(value: Decision) =>
                set("specialChecks", { ...review.specialChecks, [key]: value })
              }
            />
          ))}
      </div>
      <label className={`${labelStyle} mt-5`}>
        证据来源与报告期
        <textarea
          aria-label="证据来源与报告期"
          maxLength={4000}
          value={review.evidenceSource ?? ""}
          onChange={(event) => set("evidenceSource", event.target.value)}
          rows={3}
          className={inputStyle}
          placeholder="填写财报/公告链接、报告期及估值依据；不以新闻标题代替财务证据"
        />
      </label>
      <label className={`${labelStyle} mt-4`}>
        为什么现在不应该买
        <textarea
          aria-label="为什么现在不应该买"
          maxLength={4000}
          value={review.counterArgument ?? ""}
          onChange={(event) => set("counterArgument", event.target.value)}
          rows={2}
          className={inputStyle}
          placeholder="记录反方证据和需要持续跟踪的风险"
        />
      </label>
    </>
  );
}
