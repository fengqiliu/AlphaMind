"use client";

import { useCallback, useEffect, useState } from "react";
import { BellRing, Check, Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/components/common/Button";
import {
  acknowledgePriceAlert,
  checkPriceAlerts,
  getPriceAlerts,
} from "@/api/alerts";
import type { PriceAlert } from "@/types/alerts";

const ZONE_LABELS: Record<string, string> = {
  UNKNOWN: "首次检查",
  ABOVE_FIRST: "首档之上",
  BETWEEN_BANDS: "档间",
  TIER_1: "第 1 档",
  TIER_2: "第 2 档",
  TIER_3: "第 3 档",
  BELOW_THIRD: "跌破第 3 档",
};

const zoneLabel = (zone: string) => ZONE_LABELS[zone] ?? zone;

const formatTime = (value: string) =>
  new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });

/** 未读价格提醒：价格进入更深档位时由后端定时检查生成，只提示去复核。 */
export function PriceAlertPanel({
  onReview,
}: {
  onReview: (stockCode: string) => void;
}) {
  const [alerts, setAlerts] = useState<PriceAlert[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const load = useCallback(async () => {
    try {
      setAlerts(await getPriceAlerts());
      setLoadError(null);
    } catch (failure) {
      setLoadError(failure instanceof Error ? failure.message : "价格提醒加载失败");
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    getPriceAlerts()
      .then((items) => {
        if (!cancelled) setAlerts(items);
      })
      .catch((failure) => {
        if (!cancelled)
          setLoadError(failure instanceof Error ? failure.message : "价格提醒加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const acknowledge = async (id: number) => {
    setActionMessage(null);
    try {
      await acknowledgePriceAlert(id);
      setAlerts((previous) => previous?.filter((item) => item.id !== id) ?? null);
    } catch (failure) {
      setActionMessage(failure instanceof Error ? failure.message : "标记已读失败");
    }
  };

  const check = async () => {
    setChecking(true);
    setActionMessage(null);
    try {
      const summary = await checkPriceAlerts();
      const skipped = summary.skippedStale.length + summary.failed.length;
      setActionMessage(
        `已检查 ${summary.checked} 个标的，新提醒 ${summary.created} 条` +
          (skipped ? `；${skipped} 个因行情过期或取数失败未检查` : ""),
      );
      await load();
    } catch (failure) {
      setActionMessage(failure instanceof Error ? failure.message : "档位检查失败");
    } finally {
      setChecking(false);
    }
  };

  return (
    <section className="glass-card p-5 sm:p-6" aria-labelledby="price-alerts-title">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 id="price-alerts-title" className="flex items-center gap-2 text-sm font-semibold">
          <BellRing className="size-4 text-[var(--accent)]" />
          价格提醒
          {alerts && alerts.length > 0 && (
            <span className="rounded-full bg-[var(--accent)] px-2 py-0.5 text-[10px] text-white">
              {alerts.length} 条未读
            </span>
          )}
        </h2>
        <Button type="button" variant="outline" size="sm" disabled={checking} onClick={() => void check()}>
          {checking ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
          立即检查
        </Button>
      </div>
      <p className="text-xs leading-6 text-[var(--text-muted)]">
        交易时段每 15 分钟检查一次；价格首次进入更深的档位时提醒一次，回到首档之上后重新计数。提醒只表示该去复核，不是买入信号；过期行情不会触发。
      </p>
      {actionMessage && (
        <p role="status" className="mt-3 rounded-xl bg-[var(--neutral-glow)] p-3 text-xs text-[var(--text-secondary)]">
          {actionMessage}
        </p>
      )}
      {loadError ? (
        <div role="alert" className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--bearish-glow)] p-3 text-xs text-[var(--bearish)]">
          {loadError}
          <Button type="button" variant="outline" size="sm" onClick={() => void load()}>
            重试
          </Button>
        </div>
      ) : alerts === null ? (
        <p role="status" className="mt-3 text-xs text-[var(--text-muted)]">
          正在读取提醒...
        </p>
      ) : alerts.length === 0 ? (
        <p className="mt-3 text-xs text-[var(--text-muted)]">暂无未读提醒。</p>
      ) : (
        <ul className="mt-3 divide-y divide-[var(--border)]">
          {alerts.map((alert) => (
            <li key={alert.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0 text-sm">
                <p className="font-medium">
                  {alert.stockName}
                  <span className="ml-2 font-mono text-xs text-[var(--text-muted)]">{alert.stockCode}</span>
                </p>
                <p className="mt-1 text-xs text-[var(--text-secondary)]">
                  {zoneLabel(alert.fromZone)} → <span className="font-medium text-[var(--accent)]">{zoneLabel(alert.toZone)}</span>
                  <span className="ml-2 font-mono">{alert.price.toFixed(alert.price < 10 ? 3 : 2)}</span>
                  <span className="ml-2 text-[var(--text-muted)]">
                    {formatTime(alert.triggeredAt)} · v{alert.modelVersion}
                  </span>
                </p>
              </div>
              <div className="flex gap-2">
                <Button type="button" size="sm" onClick={() => onReview(alert.stockCode)}>
                  去复核
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`将 ${alert.stockName} 的提醒标为已读`}
                  onClick={() => void acknowledge(alert.id)}
                >
                  <Check className="size-3.5" />
                  已读
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
