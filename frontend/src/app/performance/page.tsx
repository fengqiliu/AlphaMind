"use client";

import { useEffect, useState } from "react";
import { Target } from "lucide-react";
import { getSignalPerformance } from "@/api/ledger";
import type { PerformanceDimension, PerformanceRow, PerformanceSummary } from "@/types/ledger";
import { Button } from "@/components/common/Button";
import { HORIZONS, pct, toneOf } from "@/components/ledger/format";
import { modeLabel, strategyLabel } from "@/utils/reportMetadata";

const SECTIONS: { dimension: PerformanceDimension; title: string; note: string }[] = [
  { dimension: "signal", title: "按信号类型", note: "BUY 跑赢沪深 300 算对，SELL 跑输算对；HOLD 只看收益，不算胜率。" },
  { dimension: "buyConfidence", title: "BUY 信号按规则一致度", note: "检验“一致度越高越准”是否成立。" },
  { dimension: "mode", title: "按分析模式", note: "流水线与辩论模式的对比。" },
  { dimension: "strategy", title: "按策略", note: "胜率只统计有方向的 BUY / SELL。" },
  { dimension: "personalLevel", title: "个人投资模型等级", note: "S0–S4 复核结论之后的实际表现。" },
];

const SIGNAL_LABEL: Record<string, string> = { BUY: "买入", SELL: "卖出", HOLD: "观望" };

function keyLabel(dimension: PerformanceDimension, key: string) {
  if (dimension === "signal") return SIGNAL_LABEL[key] ?? key;
  if (dimension === "mode" && key !== "未记录") return modeLabel(key as never);
  if (dimension === "strategy" && key !== "未记录") return strategyLabel(key as never);
  return key;
}

function Cell({ row }: { row: PerformanceRow | undefined }) {
  if (!row) return <td className="px-3 py-3 text-[var(--text-muted)]">—</td>;
  return (
    <td className={`px-3 py-3 align-top ${row.insufficient ? "opacity-60" : ""}`}>
      <div className={`font-mono font-semibold ${toneOf(row.meanExcessReturn)}`}>{pct(row.meanExcessReturn)}</div>
      <div className="mt-1 text-[11px] text-[var(--text-secondary)]">
        胜率 {row.hitRate == null ? "—" : `${(row.hitRate * 100).toFixed(0)}%`} · {row.count} 条
      </div>
      {row.insufficient && <div className="text-[11px] text-[var(--neutral)]">样本不足</div>}
    </td>
  );
}

export default function PerformancePage() {
  const [summary, setSummary] = useState<PerformanceSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getSignalPerformance()
      .then((data) => {
        if (!cancelled) {
          setSummary(data);
          setError(null);
        }
      })
      .catch(() => !cancelled && setError("信号表现加载失败，请检查后端服务后重试"));
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  return (
    <div className="space-y-6 px-4 py-6 sm:px-7 lg:px-9 lg:py-8">
      <header className="flex flex-wrap items-center justify-between gap-4 animate-enter">
        <div>
          <p className="mb-2 text-[11px] font-medium text-[var(--accent)]">事后复盘</p>
          <h1 className="text-3xl font-semibold tracking-tight">信号表现</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--text-secondary)]">
            每份报告到期后自动记账：报告日之后第一个交易日开盘买入，持有 5 / 10 / 20 个交易日收盘卖出，扣除约 0.2%
            往返成本，与同期沪深 300 比较。这里只记录发生了什么，不据此修改评分规则。
          </p>
        </div>
        {summary && (
          <span className="inline-flex shrink-0 items-center gap-2 whitespace-nowrap rounded-full border border-[var(--border)] bg-white px-4 py-2 text-xs text-[var(--text-secondary)]">
            <Target className="h-3.5 w-3.5 text-[var(--accent)]" />
            已记账 {summary.evaluatedCount} 条
          </span>
        )}
      </header>

      {error && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--bearish-glow)] p-4 text-sm text-[var(--bearish)]">
          {error}
          <Button variant="outline" size="sm" onClick={() => setAttempt((v) => v + 1)}>
            重新加载
          </Button>
        </div>
      )}

      {!summary && !error && (
        <p role="status" className="py-8 text-sm text-[var(--text-muted)]">正在读取信号账本…</p>
      )}

      {summary && summary.evaluatedCount === 0 && (
        <section className="glass-card p-6 text-sm leading-7 text-[var(--text-secondary)]">
          还没有到期的信号。报告生成后至少需要 5 个交易日才会出现第一条记录；每个交易日 16:10 自动补算。
        </section>
      )}

      {summary &&
        summary.evaluatedCount > 0 &&
        SECTIONS.map(({ dimension, title, note }) => {
          const rows = summary.rows.filter((r) => r.dimension === dimension);
          if (rows.length === 0) return null;
          const keys = [...new Set(rows.map((r) => r.key))];
          return (
            <section key={dimension} className="glass-card overflow-hidden">
              <div className="border-b border-[var(--border)] p-4">
                <h2 className="text-base font-semibold">{title}</h2>
                <p className="mt-1 text-xs text-[var(--text-muted)]">{note}</p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[520px] text-sm">
                  <thead>
                    <tr className="text-left text-xs text-[var(--text-muted)]">
                      <th className="px-3 py-2 font-medium">分组</th>
                      {HORIZONS.map((h) => (
                        <th key={h} className="px-3 py-2 font-medium">
                          {h} 日平均超额
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--border)]">
                    {keys.map((key) => (
                      <tr key={key}>
                        <th scope="row" className="px-3 py-3 text-left font-medium">
                          {keyLabel(dimension, key)}
                        </th>
                        {HORIZONS.map((h) => (
                          <Cell key={h} row={rows.find((r) => r.key === key && r.horizonDays === h)} />
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          );
        })}

      {summary && summary.blockedCount > 0 && (
        <p className="text-xs text-[var(--text-muted)]">
          另有 {summary.blockedCount} 条因入场日一字涨停无法买入，未计入统计。
        </p>
      )}
      <p className="text-xs leading-6 text-[var(--text-muted)]">
        样本少于 20 条的分组标为“样本不足”，结论仅供参考；历史表现不代表未来，不构成投资建议。
      </p>
    </div>
  );
}
