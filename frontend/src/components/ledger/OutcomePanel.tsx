"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { getReportOutcomes } from "@/api/ledger";
import type { SignalOutcome } from "@/types/ledger";
import { HORIZONS, pct, toneOf } from "./format";

/** 历史详情中的“事后表现”：这份报告之后 5/10/20 个交易日实际发生了什么。 */
export function OutcomePanel({ reportId }: { reportId: string }) {
  const [result, setResult] = useState<{
    id: string;
    rows: SignalOutcome[] | null;
    error: string | null;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getReportOutcomes(reportId)
      .then((rows) => !cancelled && setResult({ id: reportId, rows, error: null }))
      .catch(() => !cancelled && setResult({ id: reportId, rows: null, error: "事后表现加载失败" }));
    return () => {
      cancelled = true;
    };
  }, [reportId]);

  const current = result?.id === reportId ? result : null;
  const byHorizon = new Map(current?.rows?.map((row) => [row.horizonDays, row]) ?? []);

  return (
    <section aria-label="事后表现" className="glass-card p-4">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-base font-semibold">事后表现</h3>
        <Link href="/performance" className="text-xs text-[var(--accent)]">
          查看全部信号表现 →
        </Link>
      </div>
      {!current ? (
        <p className="text-xs text-[var(--text-muted)]">正在读取…</p>
      ) : current.error ? (
        <p role="alert" className="text-xs text-[var(--bearish)]">{current.error}</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-3">
          {HORIZONS.map((h) => {
            const row = byHorizon.get(h);
            return (
              <div key={h} className="rounded-xl bg-[var(--bg-tertiary)] p-3 text-xs">
                <p className="text-[var(--text-muted)]">持有 {h} 个交易日</p>
                {!row ? (
                  <p className="mt-2 text-[var(--text-secondary)]">尚未到期</p>
                ) : row.entryBlocked ? (
                  <p className="mt-2 text-[var(--text-secondary)]">次日一字涨停，无法买入，不计入统计</p>
                ) : (
                  <>
                    <p className={`mt-2 font-mono text-lg font-semibold ${toneOf(row.excessReturn)}`}>
                      {pct(row.excessReturn)}
                    </p>
                    <p className="text-[var(--text-muted)]">相对沪深 300 超额</p>
                    <p className="mt-2 text-[var(--text-secondary)]">
                      净收益 <span className={toneOf(row.netReturn)}>{pct(row.netReturn)}</span> · 沪深 300{" "}
                      {pct(row.benchmarkReturn)}
                    </p>
                    <p className="mt-1 font-mono text-[11px] text-[var(--text-muted)]">
                      {row.entryDate} 开盘 {row.entryPrice.toFixed(2)} → {row.exitDate} 收盘 {row.exitPrice.toFixed(2)}
                    </p>
                  </>
                )}
              </div>
            );
          })}
        </div>
      )}
      <p className="mt-3 text-[11px] leading-5 text-[var(--text-muted)]">
        口径：报告日之后第一个交易日开盘买入，持有期满收盘卖出，扣除约 0.2% 往返成本；历史表现不代表未来。
      </p>
    </section>
  );
}
