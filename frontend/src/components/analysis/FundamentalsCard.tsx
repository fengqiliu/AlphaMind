import type { Fundamentals } from "@/types/investment";

const fixed = (value?: number | null, digits = 2, suffix = "") =>
  value == null ? "—" : `${value.toFixed(digits)}${suffix}`;
const signed = (value?: number | null) =>
  value == null ? "—" : `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
const yi = (value?: number | null) =>
  value == null ? "—" : `${(value / 1e8).toFixed(2)} 亿`;
const percentile = (value?: number | null) =>
  value == null ? "分位不适用" : `${value.toFixed(1)}% 分位`;

/** 自动取得的财报与估值，只作复核参考，不给结论。 */
export function FundamentalsCard({
  data,
  title = "自动取数：财报与估值",
}: {
  data: Fundamentals;
  title?: string;
}) {
  const v = data.valuation;
  const f = data.financials;
  const statusLabel =
    data.status === "STALE_CACHE"
      ? "过期缓存"
      : data.status === "FRESH_CACHE"
        ? "新鲜缓存"
        : "来源最新";
  const cells: [string, string, string?][] = [];
  if (v) {
    cells.push(
      ["PE_TTM", fixed(v.peTtm), percentile(v.pePercentile)],
      ["PB_MRQ", fixed(v.pbMrq), percentile(v.pbPercentile)],
      ["总市值", yi(v.totalMarketCap), `截至 ${v.tradeDate}`],
      ["PCF_OCF_TTM", fixed(v.pcfOcfTtm)],
    );
  }
  if (f) {
    cells.push(
      [
        "扣非净利润",
        yi(f.deductedNetProfit),
        `同比 ${signed(f.deductedNetProfitYoy)}`,
      ],
      ["营业收入", yi(f.revenue), `同比 ${signed(f.revenueYoy)}`],
      [
        "每股经营现金流",
        fixed(f.ocfPerShare, 2, " 元"),
        `基本 EPS ${fixed(f.eps)}`,
      ],
      [
        "ROE（加权）",
        fixed(f.roe, 2, "%"),
        `资产负债率 ${fixed(f.debtRatio, 2, "%")}`,
      ],
    );
  }
  return (
    <div className="rounded-xl border border-[var(--border)] p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold">{title}</h3>
        <p className="text-[11px] text-[var(--text-muted)]">
          {data.source} · {statusLabel} · 取数日 {data.retrievedOn}
          {f && ` · ${f.reportName ?? f.reportDate}`}
        </p>
      </div>
      {data.warning && (
        <p
          role="alert"
          className="mt-3 rounded-lg bg-[var(--neutral-glow)] p-2 text-xs text-[var(--neutral)]"
        >
          {data.warning}
        </p>
      )}
      {cells.length > 0 && (
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {cells.map(([label, value, hint]) => (
            <div key={label} className="rounded-lg bg-[var(--bg-tertiary)] p-3">
              <p className="text-[11px] text-[var(--text-muted)]">{label}</p>
              <p className="mt-1 font-mono text-sm font-semibold">{value}</p>
              {hint && (
                <p className="mt-1 text-[11px] text-[var(--text-secondary)]">
                  {hint}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
      <ul className="mt-3 list-disc space-y-1 pl-4 text-xs leading-6 text-[var(--text-secondary)]">
        {data.observations.map((note) => (
          <li key={note}>{note}</li>
        ))}
      </ul>
      <p className="mt-2 text-[11px] leading-5 text-[var(--text-muted)]">
        {data.disclaimer}
      </p>
    </div>
  );
}
