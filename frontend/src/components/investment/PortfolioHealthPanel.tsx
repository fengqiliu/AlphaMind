"use client";

import { useEffect, useState } from "react";
import { BellRing, Check, Loader2, RefreshCw, Stethoscope } from "lucide-react";
import { Button } from "@/components/common/Button";
import { acknowledgeHealthAlert, getHealthAlerts, getPortfolioHealth } from "@/api/portfolio";
import type { HealthAlert, HealthKind, PortfolioHealth } from "@/types/portfolio";

const KIND_LABELS: Record<HealthKind, string> = {
  POSITION_LIMIT: "单标的超线",
  THEME_LIMIT: "主题超线",
  CASH_FLOOR: "现金不足",
  DRAWDOWN: "回撤越线",
  OVERHEATED: "过热",
  REVIEW_MISSING: "未复核",
  REVIEW_EXPIRED: "证据过期",
  REVIEW_EXPIRING: "证据即将过期",
};

/** 提前提示，不算越线：用中性色。 */
const ADVISORY: ReadonlySet<HealthKind> = new Set(["REVIEW_EXPIRING"]);

const formatTime = (value: string) =>
  new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });

/** 每日收盘后自动体检产生的新提醒：某项首次越线时记一条，持续越线不重复。 */
function HealthAlertList({
  assetCodes,
  onReview,
}: {
  assetCodes: string[];
  onReview: (stockCode: string) => void;
}) {
  const [alerts, setAlerts] = useState<HealthAlert[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getHealthAlerts()
      .then((items) => {
        if (!cancelled) setAlerts(items);
      })
      .catch((failure) => {
        if (!cancelled) setError(failure instanceof Error ? failure.message : "体检提醒加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const acknowledge = async (id: number) => {
    setError(null);
    try {
      await acknowledgeHealthAlert(id);
      setAlerts((previous) => previous?.filter((item) => item.id !== id) ?? null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "标记已读失败");
    }
  };

  if (error)
    return (
      <p role="alert" className="mt-3 rounded-xl bg-[var(--bearish-glow)] p-3 text-xs text-[var(--bearish)]">
        {error}
      </p>
    );
  if (!alerts || alerts.length === 0) return null;
  return (
    <div className="mt-3 rounded-xl border border-[var(--border)] p-3">
      <p className="flex items-center gap-2 text-xs font-medium">
        <BellRing className="size-3.5 text-[var(--accent)]" />
        每日体检新提醒
        <span className="rounded-full bg-[var(--accent)] px-2 py-0.5 text-[10px] text-white">{alerts.length} 条未读</span>
      </p>
      <ul className="mt-2 divide-y divide-[var(--border)]">
        {alerts.map((alert) => (
          <li key={alert.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
            <div className="min-w-0 text-sm">
              <p className="font-medium">
                <span className="mr-2 text-[10px] text-[var(--text-muted)]">{KIND_LABELS[alert.kind] ?? alert.kind}</span>
                {alert.name}
                {alert.stockCode && (
                  <span className="ml-2 font-mono text-xs text-[var(--text-muted)]">{alert.stockCode}</span>
                )}
              </p>
              <p className="mt-1 text-xs text-[var(--text-secondary)]">
                {alert.message}
                <span className="ml-2 text-[var(--text-muted)]">{formatTime(alert.triggeredAt)}</span>
              </p>
            </div>
            <div className="flex gap-2">
              {alert.stockCode && assetCodes.includes(alert.stockCode) && (
                <Button type="button" size="sm" onClick={() => onReview(alert.stockCode!)}>
                  去复核
                </Button>
              )}
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label={`将 ${alert.name} 的体检提醒标为已读`}
                onClick={() => void acknowledge(alert.id)}
              >
                <Check className="size-3.5" />
                已读
              </Button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

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
        按模型已有参数检查已录入持仓：单标的控制线（成长科技 3%、其余 5%）、模型主题合计 20%、现金底线 15%、组合回撤 20%、日 RSI14 &gt; 70，以及证据是否过期或 7 天内到期。只提示复核，不给卖出价或减仓比例。工作日 15:40 自动体检一次，新出现的越线项会留下提醒。
      </p>
      <HealthAlertList assetCodes={assetCodes} onReview={onReview} />
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
                      <span
                        className={`mr-2 rounded px-1.5 py-0.5 text-[10px] ${
                          ADVISORY.has(finding.kind)
                            ? "bg-[var(--neutral-glow)] text-[var(--text-secondary)]"
                            : "bg-[var(--bearish-glow)] text-[var(--bearish)]"
                        }`}
                      >
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
