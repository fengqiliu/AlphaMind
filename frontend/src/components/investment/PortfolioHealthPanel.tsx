"use client";

import { useEffect, useState } from "react";
import { Loader2, RefreshCw, Stethoscope } from "lucide-react";
import { Button } from "@/components/common/Button";
import { getPortfolioHealth } from "@/api/portfolio";
import type { HealthKind, PortfolioHealth } from "@/types/portfolio";

const KIND_LABELS: Record<HealthKind, string> = {
  POSITION_LIMIT: "单标的超线",
  THEME_LIMIT: "主题超线",
  CASH_FLOOR: "现金不足",
  DRAWDOWN: "回撤越线",
  OVERHEATED: "过热",
  REVIEW_MISSING: "未复核",
  REVIEW_EXPIRED: "证据过期",
};

/**
 * 持仓体检：按模型已有参数检查已录入持仓，只提示需要复核的项，不给卖出价或减仓比例。
 * refreshKey 变化（如保存持仓后）时重新检查。
 */
export function PortfolioHealthPanel({
  refreshKey,
  assetCodes,
  onReview,
}: {
  refreshKey: number;
  /** 模型内标的代码；只有这些可以“去复核”。 */
  assetCodes: string[];
  onReview: (stockCode: string) => void;
}) {
  const [attempt, setAttempt] = useState(0);
  const requestKey = `${refreshKey}-${attempt}`;
  const [result, setResult] = useState<{
    key: string;
    health?: PortfolioHealth | null;
    error?: string;
  } | null>(null);
  // 结果属于哪一次请求：key 不一致即为加载中，避免在 effect 中同步 setState。
  const loading = result?.key !== requestKey;
  const health = loading ? undefined : result?.health;
  const error = loading ? null : (result?.error ?? null);

  useEffect(() => {
    let cancelled = false;
    getPortfolioHealth()
      .then((value) => {
        if (!cancelled) setResult({ key: requestKey, health: value });
      })
      .catch((failure) => {
        if (!cancelled)
          setResult({
            key: requestKey,
            error: failure instanceof Error ? failure.message : "持仓体检失败",
          });
      });
    return () => {
      cancelled = true;
    };
  }, [requestKey]);

  return (
    <section className="glass-card p-5 sm:p-6" aria-labelledby="portfolio-health-title">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 id="portfolio-health-title" className="flex items-center gap-2 text-sm font-semibold">
          <Stethoscope className="size-4 text-[var(--accent)]" />
          持仓体检
          {health && health.findings.length > 0 && (
            <span className="rounded-full bg-[var(--bearish)] px-2 py-0.5 text-[10px] text-white">
              {health.findings.length} 项需复核
            </span>
          )}
        </h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={loading}
          onClick={() => setAttempt((n) => n + 1)}
        >
          {loading ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
          重新检查
        </Button>
      </div>
      <p className="text-xs leading-6 text-[var(--text-muted)]">
        按模型已有参数检查已录入持仓：单标的控制线（成长科技 3%、其余 5%）、模型主题合计 20%、现金底线 15%、组合回撤 20%、日 RSI14 &gt; 70，以及证据是否过期。只提示复核，不给卖出价或减仓比例。
      </p>
      {error ? (
        <p role="alert" className="mt-3 rounded-xl bg-[var(--bearish-glow)] p-3 text-xs text-[var(--bearish)]">
          {error}
        </p>
      ) : health === undefined ? (
        <p role="status" className="mt-3 text-xs text-[var(--text-muted)]">
          正在用实时行情检查持仓...
        </p>
      ) : health === null ? (
        <p className="mt-3 text-xs text-[var(--text-muted)]">尚未录入持仓，在下方“我的持仓”中录入后即可体检。</p>
      ) : (
        <div className="mt-3 space-y-3">
          {health.unavailable.length > 0 && (
            <div role="status" className="rounded-xl bg-[var(--neutral-glow)] p-3 text-xs leading-6 text-[var(--text-secondary)]">
              <p className="font-medium">以下持仓无当期行情，组合权重、现金与回撤未检查：</p>
              <ul className="list-disc pl-4">
                {health.unavailable.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          )}
          {health.findings.length === 0 ? (
            <p className="text-xs text-[var(--text-muted)]">
              {health.unavailable.length > 0 ? "其余可检查项未发现越线。" : "未发现越线、过热或过期证据。"}
            </p>
          ) : (
            <ul className="divide-y divide-[var(--border)]">
              {health.findings.map((finding) => (
                <li
                  key={`${finding.kind}-${finding.stockCode ?? finding.name}`}
                  className="flex flex-wrap items-center justify-between gap-3 py-3"
                >
                  <div className="min-w-0 text-sm">
                    <p className="font-medium">
                      <span className="mr-2 rounded bg-[var(--bearish-glow)] px-1.5 py-0.5 text-[10px] text-[var(--bearish)]">
                        {KIND_LABELS[finding.kind]}
                      </span>
                      {finding.name}
                      {finding.stockCode && (
                        <span className="ml-2 font-mono text-xs text-[var(--text-muted)]">{finding.stockCode}</span>
                      )}
                    </p>
                    <p className="mt-1 text-xs text-[var(--text-secondary)]">{finding.message}</p>
                  </div>
                  {finding.stockCode && assetCodes.includes(finding.stockCode) && (
                    <Button type="button" size="sm" onClick={() => onReview(finding.stockCode!)}>
                      去复核
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          <p className="text-[10px] text-[var(--text-muted)]">
            检查于 {new Date(health.checkedAt).toLocaleString("zh-CN")}
            {health.totalValue != null && ` · 总资产 ${health.totalValue.toLocaleString("zh-CN")} 元`}
          </p>
        </div>
      )}
    </section>
  );
}
