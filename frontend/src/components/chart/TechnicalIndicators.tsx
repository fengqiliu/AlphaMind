"use client";

import { cn } from "@/utils";
import type { TechnicalIndicators } from "@/types";

interface TechnicalIndicatorsPanelProps {
  data: TechnicalIndicators;
  className?: string;
}

function IndicatorCard({
  title,
  value,
  signal,
}: {
  title: string;
  value: number;
  signal: "overbought" | "oversold" | "neutral";
}) {
  const colors = {
    overbought: "text-[var(--bearish)]",
    oversold: "text-[var(--bullish)]",
    neutral: "text-[var(--text-primary)]",
  };

  const bgColors = {
    overbought: "bg-[var(--bearish-glow)] border-[var(--bearish)]/30",
    oversold: "bg-[var(--bullish-glow)] border-[var(--bullish)]/30",
    neutral: "bg-[var(--bg-tertiary)] border-[var(--border)]",
  };

  return (
    <div className={cn("p-3 rounded-xl border", bgColors[signal])}>
      <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-1">
        {title}
      </div>
      <div className={cn("text-lg font-bold font-mono", colors[signal])}>
        {value.toFixed(2)}
      </div>
    </div>
  );
}

function getSignal(
  value: number,
  overbought: number,
  oversold: number,
): "overbought" | "oversold" | "neutral" {
  if (value >= overbought) return "overbought";
  if (value <= oversold) return "oversold";
  return "neutral";
}

export function TechnicalIndicatorsPanel({
  data,
  className,
}: TechnicalIndicatorsPanelProps) {
  const { macd, rsi, kdj, bollinger } = data;

  const getScoreColor = (score: number) => {
    if (score >= 70) return "text-[var(--bullish)]";
    if (score >= 40) return "text-[var(--neutral)]";
    return "text-[var(--bearish)]";
  };

  const getScoreGradient = (score: number) => {
    if (score >= 70) return "from-[var(--bullish)] to-[#00ff88]";
    if (score >= 40) return "from-[var(--neutral)] to-[#ffcc00]";
    return "from-[var(--bearish)] to-[#ff6699]";
  };

  return (
    <div className={cn("space-y-5", className)}>
      {/* MACD */}
      <div>
        <h4 className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3 flex items-center gap-2">
          <span className="w-1 h-4 bg-[var(--accent)] rounded-full" />
          MACD
        </h4>
        <div className="grid grid-cols-3 gap-2">
          <IndicatorCard title="DIF" value={macd.dif} signal="neutral" />
          <IndicatorCard title="DEA" value={macd.dea} signal="neutral" />
          <IndicatorCard
            title="MACD柱"
            value={macd.histogram}
            signal={macd.histogram > 0 ? "oversold" : macd.histogram < 0 ? "overbought" : "neutral"}
          />
        </div>
      </div>

      {/* RSI */}
      <div>
        <h4 className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3 flex items-center gap-2">
          <span className="w-1 h-4 bg-[var(--neutral)] rounded-full" />
          RSI
        </h4>
        <div className="grid grid-cols-3 gap-2">
          <IndicatorCard
            title="RSI(6)"
            value={rsi.rsi6}
            signal={getSignal(rsi.rsi6, 70, 30)}
          />
          <IndicatorCard
            title="RSI(12)"
            value={rsi.rsi12}
            signal={getSignal(rsi.rsi12, 70, 30)}
          />
          <IndicatorCard
            title="RSI(24)"
            value={rsi.rsi24}
            signal={getSignal(rsi.rsi24, 70, 30)}
          />
        </div>
      </div>

      {/* KDJ */}
      <div>
        <h4 className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3 flex items-center gap-2">
          <span className="w-1 h-4 bg-[#8b5cf6] rounded-full" />
          KDJ
        </h4>
        <div className="grid grid-cols-3 gap-2">
          <IndicatorCard
            title="K"
            value={kdj.k}
            signal={getSignal(kdj.k, 80, 20)}
          />
          <IndicatorCard
            title="D"
            value={kdj.d}
            signal={getSignal(kdj.d, 80, 20)}
          />
          <IndicatorCard
            title="J"
            value={kdj.j}
            signal={getSignal(kdj.j, 90, 10)}
          />
        </div>
      </div>

      {/* 布林带 */}
      <div>
        <h4 className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3 flex items-center gap-2">
          <span className="w-1 h-4 bg-[#8b5cf6] rounded-full" />
          BOLLINGER
        </h4>
        <div className="grid grid-cols-3 gap-2">
          <div className="p-3 rounded-xl bg-[var(--accent-subtle)] border border-[var(--accent)]/20">
            <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-1">
              上轨
            </div>
            <div className="text-lg font-bold font-mono text-[var(--accent)]">
              {bollinger.upper.toFixed(2)}
            </div>
          </div>
          <div className="p-3 rounded-xl bg-[var(--bg-tertiary)] border border-[var(--border)]">
            <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-1">
              中轨
            </div>
            <div className="text-lg font-bold font-mono text-[var(--text-primary)]">
              {bollinger.middle.toFixed(2)}
            </div>
          </div>
          <div className="p-3 rounded-xl bg-[var(--accent-subtle)] border border-[var(--accent)]/20">
            <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-1">
              下轨
            </div>
            <div className="text-lg font-bold font-mono text-[var(--accent)]">
              {bollinger.lower.toFixed(2)}
            </div>
          </div>
        </div>
      </div>

      <details className="rounded-xl border border-[var(--border)] p-3" open>
        <summary className="cursor-pointer text-sm font-medium">技术评分依据</summary>
        <p className="mt-2 text-xs leading-relaxed text-[var(--text-secondary)]">
          {data.scoreExplanation || "旧报告未记录技术评分口径，不按新规则重算。"}
        </p>
        {data.scoreContributions && (
          <ul className="mt-2 flex flex-wrap gap-2 text-xs text-[var(--text-muted)]">
            {Object.entries(data.scoreContributions).map(([name, points]) => (
              <li className="rounded bg-[var(--bg-tertiary)] px-2 py-1" key={name}>
                {name} {points > 0 ? "+" : ""}{points}
              </li>
            ))}
          </ul>
        )}
      </details>

      {/* 综合评分 */}
      <div className="pt-4 border-t border-[var(--border)]">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-mono text-[var(--text-muted)] tracking-wider">
            技术评分
          </span>
          <span
            className={cn(
              "text-2xl font-bold font-mono",
              getScoreColor(data.technicalScore),
            )}
          >
            {data.technicalScore.toFixed(0)}
            <span className="text-sm text-[var(--text-muted)]">/100</span>
          </span>
        </div>
        <div className="h-3 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
          <div
            className={cn(
              "h-full rounded-full transition-all duration-500 bg-gradient-to-r",
              getScoreGradient(data.technicalScore),
            )}
            style={{
              width: `${data.technicalScore}%`,
              boxShadow: `0 0 10px ${data.technicalScore >= 70 ? "var(--bullish-glow)" : data.technicalScore >= 40 ? "var(--neutral-glow)" : "var(--bearish-glow)"}`,
            }}
          />
        </div>
      </div>
    </div>
  );
}
