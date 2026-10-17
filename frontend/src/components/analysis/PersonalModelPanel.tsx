import type { PersonalModelAssessment } from "@/types/investment";
import type { MarketData } from "@/types";
import Link from "next/link";
import { CheckCircle2, CircleHelp, ShieldAlert } from "lucide-react";

const number = (value?: number | null, digits = 2) =>
  value == null ? "未提供" : value.toFixed(digits);
const states = {
  PASS: { label: "已通过", style: "text-[var(--bullish)]", icon: CheckCircle2 },
  FAIL: { label: "未通过", style: "text-[var(--bearish)]", icon: ShieldAlert },
  PENDING: {
    label: "待复核",
    style: "text-[var(--neutral)]",
    icon: CircleHelp,
  },
};

export function PersonalModelPanel({
  assessment: a,
  marketData,
}: {
  assessment: PersonalModelAssessment;
  marketData?: MarketData;
}) {
  return (
    <section aria-label="个人投资模型复核" className="space-y-5">
      {marketData && (
        <p className="text-xs leading-6 text-[var(--text-secondary)]">
          行情来源：{marketData.dataSource || "未标明"} ·{" "}
          {marketData.dataStatus === "STALE_CACHE"
            ? "过期缓存"
            : marketData.dataStatus === "FRESH_CACHE"
              ? "新鲜缓存"
              : "来源最新报价"}{" "}
          · 报价时点 {marketData.updateTime}
        </p>
      )}
      {marketData?.dataWarning && (
        <p
          role="alert"
          className="rounded-xl bg-[var(--neutral-glow)] p-3 text-xs text-[var(--neutral)]"
        >
          {marketData.dataWarning}
        </p>
      )}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs text-[var(--accent)]">
            我的投资模型 v{a.version} · 规则日期 {a.versionDate}
          </p>
          <h2 className="mt-2 text-xl font-semibold">
            {a.asset.name} · {a.asset.code}
          </h2>
        </div>
        <span className="rounded-xl bg-[var(--accent-subtle)] px-4 py-2 font-mono text-xl font-semibold text-[var(--accent)]">
          {a.level}
        </span>
      </div>
      <div
        role="status"
        className="rounded-xl border border-[var(--border)] bg-[var(--bg-tertiary)] p-4"
      >
        <p className="font-semibold">{a.action}</p>
        <p className="mt-2 text-xs leading-6 text-[var(--text-secondary)]">
          最新价格 ¥{number(a.currentPrice, 3)} ·{" "}
          {(
            {
              TIER_1: "进入首档",
              TIER_2: "进入第二档",
              TIER_3: "进入第三档",
              ABOVE_FIRST: "高于首档",
              BELOW_THIRD: "跌破第三档，需重校",
              BETWEEN_BANDS: "位于档间，仅复核",
              UNKNOWN: "价格待核实",
            } as Record<string, string>
          )[a.priceZone] ?? "待复核"}
          。价格触发不等于买入，所有数值均为复核结果，不执行交易。
        </p>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {a.asset.bands.map((band) => (
          <div
            key={band.tier}
            className="rounded-xl border border-[var(--border)] p-3"
          >
            <p className="text-[11px] text-[var(--text-muted)]">
              {["首档", "第二档", "第三档"][band.tier - 1]}
              {a.asset.provisional && " · 暂定"}
            </p>
            <p className="mt-2 font-mono text-sm">
              {band.low}–{band.high}
            </p>
          </div>
        ))}
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        {a.gates.map((gate) => {
          const state = states[gate.status];
          const Icon = state.icon;
          return (
            <article
              key={gate.id}
              className="rounded-xl border border-[var(--border)] p-4"
            >
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-semibold">{gate.label}</h3>
                <span
                  className={`inline-flex items-center gap-1 text-xs ${state.style}`}
                >
                  <Icon className="size-3.5" />
                  {state.label}
                </span>
              </div>
              <p className="mt-3 text-xs leading-6 text-[var(--text-secondary)]">
                {gate.detail}
              </p>
            </article>
          );
        })}
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          ["日 RSI14", a.timing.dailyRsi14],
          ["已结束周 RSI14", a.timing.weeklyRsi14],
          ["ATR20", a.timing.atr20],
          ["ATR / 价格 %", a.timing.atrPricePct],
        ].map(([label, value]) => (
          <div
            key={String(label)}
            className="rounded-xl bg-[var(--bg-tertiary)] p-3"
          >
            <p className="text-[11px] text-[var(--text-muted)]">{label}</p>
            <p className="mt-2 font-mono font-semibold">
              {number(value as number | null)}
            </p>
          </div>
        ))}
      </div>
      {a.timing.nextBatchPriceCeiling != null && (
        <p className="text-xs leading-6 text-[var(--text-secondary)]">
          下一档复核价格上界 ¥{number(a.timing.nextBatchPriceCeiling, 3)}
          ：取下一固定档位上沿与现价减 1.5×ATR20 的更谨慎值。仅为复核间距。
        </p>
      )}
      {a.blockers.length > 0 && (
        <div
          role="alert"
          className="rounded-xl bg-[var(--bearish-glow)] p-4 text-[var(--bearish)]"
        >
          <h3 className="mb-2 text-sm font-semibold">未通过的约束</h3>
          <ul className="list-disc space-y-1 pl-4 text-xs leading-6">
            {a.blockers.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}
      {a.missingEvidence.length > 0 && (
        <div className="rounded-xl bg-[var(--neutral-glow)] p-4">
          <h3 className="mb-2 text-sm font-semibold">待补证据</h3>
          <ul className="list-disc space-y-1 pl-4 text-xs leading-6 text-[var(--text-secondary)]">
            {a.missingEvidence.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}
      <details className="rounded-xl border border-[var(--border)] p-4 text-xs leading-6 text-[var(--text-secondary)]">
        <summary className="cursor-pointer font-medium">
          查看证据快照与规则边界
        </summary>
        <p className="mt-3">
          手工复核来源：{a.evidence.evidenceSource || "未提供"}
        </p>
        <p>反方理由：{a.evidence.counterArgument || "未提供"}</p>
        {a.evidence.sourceReportId && (
          <p>
            复核来源报告：
            <Link
              className="text-[var(--accent)] underline"
              href={`/history?report=${encodeURIComponent(a.evidence.sourceReportId)}`}
            >
              {a.evidence.sourceReportId}
            </Link>
          </p>
        )}
        <p>
          复核日 {a.evidence.reviewedAt || "未提供"} · 有效至{" "}
          {a.evidence.validUntil || "未提供"}
        </p>
        <ul className="mt-3 list-disc space-y-2 pl-4">
          {a.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
        <p className="mt-3 break-all font-mono text-[10px]">
          原文 SHA256 {a.sourceSha256}
        </p>
      </details>
    </section>
  );
}
