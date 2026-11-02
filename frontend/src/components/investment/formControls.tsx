import type { Decision, PersonalModelReview } from "@/types/investment";

export const inputStyle =
  "mt-2 w-full min-w-0 rounded-xl border border-[var(--border)] bg-white px-3 py-2.5 text-sm text-[var(--text-primary)]";
export const labelStyle =
  "block min-w-0 text-xs leading-5 text-[var(--text-secondary)]";

/** 更新复核表单中的单个字段。 */
export type ReviewSetter = <K extends keyof PersonalModelReview>(
  key: K,
  value: PersonalModelReview[K],
) => void;

export function DecisionSelect({
  label,
  value,
  onChange,
}: {
  label: string;
  value: Decision | undefined;
  onChange: (value: Decision) => void;
}) {
  return (
    <label className={labelStyle}>
      {label}
      <select
        aria-label={label}
        value={value ?? "UNKNOWN"}
        onChange={(event) => onChange(event.target.value as Decision)}
        className={inputStyle}
      >
        <option value="UNKNOWN">未确认</option>
        <option value="PASS">已复核通过</option>
        <option value="FAIL">未通过</option>
      </select>
    </label>
  );
}

export function PercentInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number | null | undefined;
  onChange: (value: number | null) => void;
}) {
  return (
    <label className={labelStyle}>
      {label}
      <input
        aria-label={label}
        type="number"
        min="0"
        max="100"
        step="0.01"
        value={value == null ? "" : String(value)}
        onChange={(event) =>
          onChange(event.target.value === "" ? null : Number(event.target.value))
        }
        className={inputStyle}
        placeholder="未提供"
      />
    </label>
  );
}
