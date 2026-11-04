/** 收益率显示为带符号百分比；空值显示“—”，不当作 0。 */
export const pct = (value: number | null | undefined, digits = 2) =>
  value == null ? "—" : `${value > 0 ? "+" : ""}${(value * 100).toFixed(digits)}%`;

export const toneOf = (value: number | null | undefined) =>
  value == null || value === 0
    ? "text-[var(--text-secondary)]"
    : value > 0
      ? "text-[var(--bullish)]"
      : "text-[var(--bearish)]";

export const HORIZONS = [5, 10, 20] as const;
