"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Save, Trash2, Wallet } from "lucide-react";
import { Button } from "@/components/common/Button";
import { getPortfolio, savePortfolio } from "@/api/portfolio";
import type { PersonalAsset } from "@/types/investment";
import type { Holding, PortfolioProfile } from "@/types/portfolio";
import { inputStyle, labelStyle } from "./formControls";

const emptyHolding = (): Holding => ({
  stockCode: null,
  name: "",
  theme: "",
  shares: null,
  manualValue: null,
});

const toNumber = (value: string) => (value === "" ? null : Number(value));

/** "我的持仓"：手动维护当前组合，用于推算 Gate 5 的交易后权重。不对接券商。 */
export function PortfolioEditor({ assets }: { assets: PersonalAsset[] }) {
  const [profile, setProfile] = useState<PortfolioProfile | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const themes = [...new Set(assets.map((asset) => asset.theme))];

  useEffect(() => {
    let cancelled = false;
    getPortfolio()
      .then((loaded) => {
        if (cancelled) return;
        setLoadError(null);
        setProfile(
          loaded ?? { holdings: [], cash: 0, lowVolatility: 0, peakValue: null },
        );
      })
      .catch((failure) => {
        if (!cancelled)
          setLoadError(failure instanceof Error ? failure.message : "持仓加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const update = (patch: Partial<PortfolioProfile>) =>
    setProfile((previous) => (previous ? { ...previous, ...patch } : previous));

  const updateHolding = (index: number, patch: Partial<Holding>) =>
    setProfile((previous) => {
      if (!previous) return previous;
      const holdings = previous.holdings.map((holding, i) => {
        if (i !== index) return holding;
        const next = { ...holding, ...patch };
        const asset = assets.find((item) => item.code === next.stockCode);
        return asset ? { ...next, name: asset.name, theme: asset.theme } : next;
      });
      return { ...previous, holdings };
    });

  const save = async () => {
    if (!profile) return;
    setSaving(true);
    setMessage(null);
    try {
      const saved = await savePortfolio(profile);
      setProfile(saved);
      setMessage("已保存。复核时可在风险预算中按持仓推算。");
    } catch (failure) {
      setMessage(failure instanceof Error ? failure.message : "持仓保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <details className="glass-card p-5 sm:p-6">
      <summary className="flex cursor-pointer items-center gap-2 text-base font-semibold">
        <Wallet className="size-4 text-[var(--accent)]" />
        我的持仓
        {profile?.updatedAt && (
          <span className="text-xs font-normal text-[var(--text-muted)]">
            更新于 {new Date(profile.updatedAt).toLocaleString("zh-CN")}
          </span>
        )}
      </summary>
      <p className="mt-4 text-xs leading-6 text-[var(--text-muted)]">
        手动维护，仅保存在本地数据库，不对接券商、不同步成交。有 6
        位代码和股数时按实时价估值；场外基金等请填手工市值。模型内标的的名称与主题以模型为准。
      </p>
      {loadError ? (
        <div
          role="alert"
          className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--bearish-glow)] p-3 text-xs text-[var(--bearish)]"
        >
          {loadError}
          <Button type="button" variant="outline" size="sm" onClick={() => setAttempt((n) => n + 1)}>
            重试
          </Button>
        </div>
      ) : !profile ? (
        <p role="status" className="mt-4 text-xs text-[var(--text-muted)]">
          正在读取持仓...
        </p>
      ) : (
        <div className="mt-4 space-y-4">
          <datalist id="portfolio-themes">
            {themes.map((theme) => (
              <option key={theme} value={theme} />
            ))}
          </datalist>
          {profile.holdings.length === 0 && (
            <p className="text-xs text-[var(--text-muted)]">还没有持仓，点“添加持仓”开始录入。</p>
          )}
          {profile.holdings.map((holding, index) => {
            const inModel = assets.some((asset) => asset.code === holding.stockCode);
            return (
              <div
                key={index}
                className="grid gap-3 rounded-xl border border-[var(--border)] p-3 sm:grid-cols-[7rem_1fr_1fr_7rem_8rem_auto]"
              >
                <label className={labelStyle}>
                  代码
                  <input
                    aria-label={`第 ${index + 1} 条持仓代码`}
                    value={holding.stockCode ?? ""}
                    maxLength={6}
                    inputMode="numeric"
                    onChange={(event) =>
                      updateHolding(index, { stockCode: event.target.value.trim() || null })
                    }
                    className={inputStyle}
                    placeholder="可空"
                  />
                </label>
                <label className={labelStyle}>
                  名称
                  <input
                    aria-label={`第 ${index + 1} 条持仓名称`}
                    value={holding.name}
                    disabled={inModel}
                    onChange={(event) => updateHolding(index, { name: event.target.value })}
                    className={inputStyle}
                  />
                </label>
                <label className={labelStyle}>
                  主题
                  <input
                    aria-label={`第 ${index + 1} 条持仓主题`}
                    list="portfolio-themes"
                    value={holding.theme}
                    disabled={inModel}
                    onChange={(event) => updateHolding(index, { theme: event.target.value })}
                    className={inputStyle}
                  />
                </label>
                <label className={labelStyle}>
                  股数
                  <input
                    aria-label={`第 ${index + 1} 条持仓股数`}
                    type="number"
                    min="0"
                    value={holding.shares ?? ""}
                    onChange={(event) => updateHolding(index, { shares: toNumber(event.target.value) })}
                    className={inputStyle}
                  />
                </label>
                <label className={labelStyle}>
                  手工市值（元）
                  <input
                    aria-label={`第 ${index + 1} 条持仓手工市值`}
                    type="number"
                    min="0"
                    value={holding.manualValue ?? ""}
                    onChange={(event) =>
                      updateHolding(index, { manualValue: toNumber(event.target.value) })
                    }
                    className={inputStyle}
                    placeholder="无实时价时填"
                  />
                </label>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="self-end"
                  aria-label={`删除第 ${index + 1} 条持仓`}
                  onClick={() =>
                    update({ holdings: profile.holdings.filter((_, i) => i !== index) })
                  }
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            );
          })}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => update({ holdings: [...profile.holdings, emptyHolding()] })}
          >
            <Plus className="size-3.5" />
            添加持仓
          </Button>
          <div className="grid gap-4 sm:grid-cols-3">
            <label className={labelStyle}>
              现金（元）
              <input
                aria-label="现金"
                type="number"
                min="0"
                value={profile.cash}
                onChange={(event) => update({ cash: Number(event.target.value) || 0 })}
                className={inputStyle}
              />
            </label>
            <label className={labelStyle}>
              低波动资产（货基、短债等，元）
              <input
                aria-label="低波动资产"
                type="number"
                min="0"
                value={profile.lowVolatility}
                onChange={(event) => update({ lowVolatility: Number(event.target.value) || 0 })}
                className={inputStyle}
              />
            </label>
            <label className={labelStyle}>
              组合历史高点（元，用于回撤）
              <input
                aria-label="组合历史高点"
                type="number"
                min="0"
                value={profile.peakValue ?? ""}
                onChange={(event) => update({ peakValue: toNumber(event.target.value) })}
                className={inputStyle}
                placeholder="未填写则回撤需手填"
              />
            </label>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button type="button" size="sm" disabled={saving} onClick={() => void save()}>
              {saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}
              保存持仓
            </Button>
            {message && (
              <p role="status" className="text-xs text-[var(--text-secondary)]">
                {message}
              </p>
            )}
          </div>
        </div>
      )}
    </details>
  );
}
