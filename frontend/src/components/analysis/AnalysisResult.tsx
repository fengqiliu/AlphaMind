"use client";

import { cn, formatNumber, formatPercent, getChangeColor } from "@/utils";
import type { TradeSignal, ConfidenceInterval } from "@/types";
import { SignalType } from "@/types";
import { ConfidenceBar } from "@/components/common/ConfidenceBar";
import {
  TrendingUp,
  TrendingDown,
  Minus,
  ArrowUp,
  Target,
  Shield,
} from "lucide-react";

interface AnalysisResultProps {
  signal: TradeSignal;
  confidence: ConfidenceInterval;
  className?: string;
}

const signalConfig = {
  [SignalType.BUY]: {
    label: "买入",
    gradient: "from-[var(--bullish)] to-[#00ff88]",
    glow: "var(--bullish-glow)",
    bg: "bg-[var(--bullish-glow)] border-[var(--bullish)]/30",
    textColor: "text-[var(--bullish)]",
    icon: TrendingUp,
  },
  [SignalType.SELL]: {
    label: "卖出",
    gradient: "from-[var(--bearish)] to-[#ff6699]",
    glow: "var(--bearish-glow)",
    bg: "bg-[var(--bearish-glow)] border-[var(--bearish)]/30",
    textColor: "text-[var(--bearish)]",
    icon: TrendingDown,
  },
  [SignalType.HOLD]: {
    label: "持有",
    gradient: "from-[var(--neutral)] to-[#ffcc00]",
    glow: "var(--neutral-glow)",
    bg: "bg-[var(--neutral-glow)] border-[var(--neutral)]/30",
    textColor: "text-[var(--neutral)]",
    icon: Minus,
  },
};

export function AnalysisResult({
  signal,
  confidence,
  className,
}: AnalysisResultProps) {
  const config = signalConfig[signal.type];
  const SignalIcon = config.icon;

  const upPercent =
    ((signal.targetPrice - signal.entryPrice) / signal.entryPrice) * 100;
  const downPercent =
    (-(signal.entryPrice - signal.stopLoss) / signal.entryPrice) * 100;

  return (
    <div className={cn("space-y-5", className)}>
      {/* Signal Header */}
      <div className={cn("p-5 rounded-xl border", config.bg)}>
        <div className="flex items-center gap-4">
          <div
            className={cn("p-3 rounded-xl bg-gradient-to-br", config.gradient)}
            style={{ boxShadow: `0 0 30px ${config.glow}` }}
          >
            <SignalIcon className="w-7 h-7 text-[var(--bg-primary)]" />
          </div>
          <div className="flex-1">
            <div className={cn("text-3xl font-bold", config.textColor)}>
              {config.label}
            </div>
            <div className="text-sm text-[var(--text-muted)] mt-1 font-mono">
              {signal.rationale}
            </div>
          </div>
        </div>
      </div>

      {/* Price Cards */}
      <div className="grid grid-cols-3 gap-4">
        <PriceCard
          label="入场价"
          value={signal.entryPrice}
          icon={<ArrowUp className="w-4 h-4 text-[var(--bullish)]" />}
          color="text-[var(--text-primary)]"
        />
        <PriceCard
          label="目标价"
          value={signal.targetPrice}
          icon={<Target className="w-4 h-4 text-[var(--accent)]" />}
          color="text-[var(--bullish)]"
        />
        <PriceCard
          label="止损价"
          value={signal.stopLoss}
          icon={<Shield className="w-4 h-4 text-[var(--bearish)]" />}
          color="text-[var(--bearish)]"
        />
      </div>

      {/* Holding Period */}
      <div className="flex items-center justify-between p-3 bg-[var(--bg-tertiary)] rounded-xl">
        <span className="text-xs font-mono text-[var(--text-muted)] tracking-wider">
          建议持仓周期
        </span>
        <span className="font-mono font-semibold text-[var(--text-primary)]">
          {signal.holdingPeriodDays} 个交易日
        </span>
      </div>

      {/* Price Range */}
      <div className="flex items-center gap-4 p-4 bg-[var(--bg-tertiary)] rounded-xl">
        <div className="flex-1">
          <div className="text-[10px] text-[var(--text-muted)] font-mono mb-1">
            上涨空间
          </div>
          <div className="text-lg font-bold font-mono text-[var(--bullish)]">
            +{upPercent.toFixed(2)}%
          </div>
        </div>
        <div className="h-10 w-px bg-[var(--border)]" />
        <div className="flex-1">
          <div className="text-[10px] text-[var(--text-muted)] font-mono mb-1">
            下跌风险
          </div>
          <div className="text-lg font-bold font-mono text-[var(--bearish)]">
            -{downPercent.toFixed(2)}%
          </div>
        </div>
        <div className="h-10 w-px bg-[var(--border)]" />
        <div className="flex-1">
          <div className="text-[10px] text-[var(--text-muted)] font-mono mb-1">
            盈亏比
          </div>
          <div className="text-lg font-bold font-mono text-[var(--accent)]">
            {(upPercent / downPercent).toFixed(2)}:1
          </div>
        </div>
      </div>

      {/* Confidence */}
      <div className="pt-4 border-t border-[var(--border)]">
        <div className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3">
          综合置信度
        </div>
        <ConfidenceBar confidence={confidence} />
      </div>
    </div>
  );
}

function PriceCard({
  label,
  value,
  icon,
  color,
}: {
  label: string;
  value: number;
  icon: React.ReactNode;
  color: string;
}) {
  return (
    <div className="text-center p-4 bg-[var(--bg-tertiary)] rounded-xl border border-[var(--border)]">
      <div className="flex items-center justify-center gap-1 mb-2 text-[var(--text-muted)]">
        {icon}
        <span className="text-[10px] font-mono tracking-wider">{label}</span>
      </div>
      <div className={cn("text-xl font-bold font-mono", color)}>
        ¥{value.toFixed(2)}
      </div>
    </div>
  );
}
