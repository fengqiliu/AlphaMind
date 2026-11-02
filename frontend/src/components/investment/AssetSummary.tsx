import type { PersonalAsset } from "@/types/investment";

/** 标的分类、原文三档价格与复核重点。 */
export function AssetSummary({ asset }: { asset: PersonalAsset }) {
  return (
    <div className="mt-5 space-y-4">
      <div className="flex flex-wrap gap-2 text-xs text-[var(--accent)]">
        <span>{asset.category}</span>
        <span>· {asset.theme}</span>
        {asset.provisional && <span>· 暂定观察区</span>}
      </div>
      <div className="grid grid-cols-3 gap-3">
        {asset.bands.map((band) => (
          <div key={band.tier} className="rounded-xl bg-[var(--bg-tertiary)] p-3">
            <p className="text-xs text-[var(--text-muted)]">
              {["首档", "第二档", "第三档"][band.tier - 1]}
            </p>
            <p className="mt-2 font-mono text-sm">
              {band.low}–{band.high}
            </p>
          </div>
        ))}
      </div>
      <p className="text-xs leading-6 text-[var(--text-secondary)]">
        估值：{asset.valuationFocus}
        <br />
        基本面：{asset.fundamentalFocus}
        <br />
        重点风险：{asset.vetoRisk}
      </p>
    </div>
  );
}
