"use client";

import { Button } from "@/components/common/Button";
import type { WeeklyStockRecommendation } from "@/types";
import { ArrowRight, Gem, Target, TrendingDown, TrendingUp } from "lucide-react";

interface WeeklyValuePicksProps {
  picks: WeeklyStockRecommendation[];
  isLoading: boolean;
  error: string | null;
  onSelect: (pick: WeeklyStockRecommendation) => void;
}

export function WeeklyValuePicks({
  picks,
  isLoading,
  error,
  onSelect,
}: WeeklyValuePicksProps) {
  const weekLabel = picks[0]?.weekLabel ?? "本周";

  return (
    <section className="glass-card-glow p-5 animate-enter delay-75">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 mb-5">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <span className="w-1.5 h-5 bg-[var(--neutral)] rounded-full" />
            每周低位价值股
          </h2>
          <p className="text-sm text-[var(--text-muted)] mt-1">
            {weekLabel} 严选 3 只估值与位置兼顾的观察标的
          </p>
        </div>
        <div className="text-xs font-mono text-[var(--text-muted)]">
          基于价格分位、行业折价、板块价值属性综合评分
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, index) => (
            <div
              key={index}
              className="rounded-2xl border border-[var(--border)] bg-[var(--bg-tertiary)]/70 p-4 animate-pulse space-y-3"
            >
              <div className="h-6 w-24 rounded bg-[var(--bg-secondary)]" />
              <div className="h-5 w-32 rounded bg-[var(--bg-secondary)]" />
              <div className="h-4 w-full rounded bg-[var(--bg-secondary)]" />
              <div className="h-4 w-5/6 rounded bg-[var(--bg-secondary)]" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-[var(--bearish)]/30 bg-[var(--bearish-glow)] p-4 text-sm text-[var(--bearish)]">
          {error}
        </div>
      ) : picks.length === 0 ? (
        <div className="rounded-2xl border border-[var(--border)] bg-[var(--bg-tertiary)] p-4 text-sm text-[var(--text-muted)]">
          本周暂未生成推荐，请稍后再试。
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
          {picks.map((pick) => {
            const isPositive = pick.changePercent >= 0;
            return (
              <article
                key={pick.stockCode}
                className="rounded-2xl border border-[var(--border)] bg-[var(--bg-tertiary)]/70 p-4 flex flex-col gap-4 hover:border-[var(--accent)]/30 transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-[var(--bg-secondary)] text-xs font-mono text-[var(--neutral)] mb-3">
                      <Gem className="w-3.5 h-3.5" />
                      TOP {pick.rank}
                    </div>
                    <h3 className="text-lg font-semibold text-[var(--text-primary)]">
                      {pick.stockName}
                    </h3>
                    <div className="mt-1 flex items-center gap-2 text-xs text-[var(--text-muted)] font-mono">
                      <span>{pick.stockCode}</span>
                      <span>·</span>
                      <span>{pick.industry}</span>
                      <span>·</span>
                      <span>{pick.market}</span>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <div className="text-lg font-bold font-mono text-[var(--text-primary)]">
                      ¥{pick.currentPrice.toFixed(2)}
                    </div>
                    <div
                      className={`mt-1 inline-flex items-center gap-1 text-xs font-mono ${isPositive ? "text-[var(--bullish)]" : "text-[var(--bearish)]"}`}
                    >
                      {isPositive ? (
                        <TrendingUp className="w-3.5 h-3.5" />
                      ) : (
                        <TrendingDown className="w-3.5 h-3.5" />
                      )}
                      {isPositive ? "+" : ""}
                      {pick.changePercent.toFixed(2)}%
                    </div>
                  </div>
                </div>

                <p className="text-sm text-[var(--text-secondary)] leading-6">
                  {pick.summary}
                </p>

                <div className="grid grid-cols-3 gap-3">
                  <ScoreBadge label="综合" value={pick.compositeScore} accent="text-[var(--accent)]" />
                  <ScoreBadge label="低位" value={pick.lowPositionScore} accent="text-[var(--neutral)]" />
                  <ScoreBadge label="价值" value={pick.valueScore} accent="text-[var(--bullish)]" />
                </div>

                <div className="rounded-xl bg-[var(--bg-secondary)]/80 p-3 space-y-2">
                  {pick.highlights.map((highlight) => (
                    <div
                      key={highlight}
                      className="flex gap-2 text-sm text-[var(--text-secondary)]"
                    >
                      <Target className="w-4 h-4 text-[var(--accent)] shrink-0 mt-0.5" />
                      <span>{highlight}</span>
                    </div>
                  ))}
                </div>

                <Button
                  variant="outline"
                  className="mt-auto border-[var(--accent)]/20 hover:border-[var(--accent)]/40"
                  onClick={() => onSelect(pick)}
                >
                  设为当前分析标的
                  <ArrowRight className="w-4 h-4" />
                </Button>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function ScoreBadge({
  label,
  value,
  accent,
}: {
  label: string;
  value: number;
  accent: string;
}) {
  return (
    <div className="rounded-xl bg-[var(--bg-secondary)] px-3 py-2">
      <div className="text-[10px] font-mono text-[var(--text-muted)] uppercase tracking-wider">
        {label}
      </div>
      <div className={`mt-1 text-lg font-bold font-mono ${accent}`}>
        {Math.round(value * 100)}
      </div>
    </div>
  );
}