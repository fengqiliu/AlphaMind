"use client";

import { cn } from "@/utils";
import type { Judgment } from "@/types";
import { DebatePosition, SignalType } from "@/types";
import { ConfidenceBar } from "@/components/common/ConfidenceBar";
import {
  ArrowUpCircle,
  ArrowDownCircle,
  MinusCircle,
  Scale,
  AlertTriangle,
  Target,
  Shield,
} from "lucide-react";

interface DebateResultProps {
  judgment: Judgment;
  className?: string;
}

const positionConfig = {
  [DebatePosition.BULLISH]: {
    label: "看多",
    gradient: "from-[var(--bullish)] to-[#00ff88]",
    glow: "var(--bullish-glow)",
    bg: "bg-[var(--bullish-glow)] border-[var(--bullish)]/30",
    textColor: "text-[var(--bullish)]",
    icon: ArrowUpCircle,
  },
  [DebatePosition.BEARISH]: {
    label: "看空",
    gradient: "from-[var(--bearish)] to-[#ff6699]",
    glow: "var(--bearish-glow)",
    bg: "bg-[var(--bearish-glow)] border-[var(--bearish)]/30",
    textColor: "text-[var(--bearish)]",
    icon: ArrowDownCircle,
  },
  [DebatePosition.NEUTRAL]: {
    label: "中立",
    gradient: "from-[var(--neutral)] to-[#ffcc00]",
    glow: "var(--neutral-glow)",
    bg: "bg-[var(--neutral-glow)] border-[var(--neutral)]/30",
    textColor: "text-[var(--neutral)]",
    icon: MinusCircle,
  },
};

const signalLabels = {
  [SignalType.BUY]: "买入",
  [SignalType.SELL]: "卖出",
  [SignalType.HOLD]: "持有",
};

const signalConfig = {
  [SignalType.BUY]: "text-[var(--bullish)]",
  [SignalType.SELL]: "text-[var(--bearish)]",
  [SignalType.HOLD]: "text-[var(--neutral)]",
};

export function DebateResult({ judgment, className }: DebateResultProps) {
  const position = positionConfig[judgment.finalPosition];
  const PositionIcon = position.icon;

  return (
    <div className={cn("space-y-5", className)}>
      {/* Final Verdict */}
      <div className={cn("p-5 rounded-xl border", position.bg)}>
        <div className="flex items-center gap-3 mb-4">
          <div
            className={cn(
              "p-3 rounded-xl bg-gradient-to-br",
              position.gradient,
            )}
            style={{ boxShadow: `0 0 30px ${position.glow}` }}
          >
            <Scale className="w-7 h-7 text-[var(--bg-primary)]" />
          </div>
          <div>
            <div className="text-xs font-mono text-[var(--text-muted)] tracking-wider">
              仲裁官裁决
            </div>
            <div
              className={cn(
                "text-2xl font-bold flex items-center gap-2 mt-1",
                position.textColor,
              )}
            >
              <PositionIcon className="w-6 h-6" />
              {position.label}
            </div>
          </div>
        </div>
        <p className="text-sm text-[var(--text-secondary)] leading-relaxed font-mono">
          {judgment.reasoning}
        </p>
      </div>

      {/* Vote Distribution */}
      <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl border border-[var(--border)]">
        <div className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-4">
          分析师投票分布
        </div>
        <div className="flex gap-3">
          <VoteBar
            position={DebatePosition.BULLISH}
            votes={judgment.voteBreakdown[DebatePosition.BULLISH]}
            total={10}
          />
          <VoteBar
            position={DebatePosition.NEUTRAL}
            votes={judgment.voteBreakdown[DebatePosition.NEUTRAL]}
            total={10}
          />
          <VoteBar
            position={DebatePosition.BEARISH}
            votes={judgment.voteBreakdown[DebatePosition.BEARISH]}
            total={10}
          />
        </div>
      </div>

      {/* Final Signal */}
      <div className="p-4 bg-[var(--accent-subtle)] rounded-xl border border-[var(--accent)]/20">
        <div className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-4 flex items-center gap-2">
          <Scale className="w-4 h-4" />
          最终交易信号
        </div>
        <div className="grid grid-cols-3 gap-4">
          <div className="text-center p-3 bg-[var(--bg-tertiary)] rounded-xl">
            <div className="text-[10px] text-[var(--text-muted)] font-mono mb-2">
              信号
            </div>
            <div
              className={cn(
                "text-xl font-bold",
                signalConfig[judgment.finalSignal.type],
              )}
            >
              {signalLabels[judgment.finalSignal.type]}
            </div>
          </div>
          <div className="text-center p-3 bg-[var(--bg-tertiary)] rounded-xl">
            <div className="text-[10px] text-[var(--text-muted)] font-mono mb-2 flex items-center justify-center gap-1">
              <Target className="w-3 h-3" />
              目标价
            </div>
            <div className="text-xl font-bold font-mono text-[var(--bullish)]">
              ¥{judgment.finalSignal.targetPrice.toFixed(2)}
            </div>
          </div>
          <div className="text-center p-3 bg-[var(--bg-tertiary)] rounded-xl">
            <div className="text-[10px] text-[var(--text-muted)] font-mono mb-2 flex items-center justify-center gap-1">
              <Shield className="w-3 h-3" />
              止损价
            </div>
            <div className="text-xl font-bold font-mono text-[var(--bearish)]">
              ¥{judgment.finalSignal.stopLoss.toFixed(2)}
            </div>
          </div>
        </div>
      </div>

      {/* Confidence */}
      <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl border border-[var(--border)]">
        <div className="text-xs font-mono text-[var(--text-muted)] tracking-wider mb-3">
          综合置信度
        </div>
        <ConfidenceBar confidence={judgment.confidence} />
      </div>

      {/* Risk Warnings */}
      {judgment.riskWarnings.length > 0 && (
        <div className="p-4 bg-[var(--neutral-glow)] rounded-xl border border-[var(--neutral)]/30">
          <div className="flex items-center gap-2 mb-3">
            <AlertTriangle className="w-5 h-5 text-[var(--neutral)]" />
            <span className="text-sm font-semibold text-[var(--neutral)]">
              风险提示
            </span>
          </div>
          <ul className="space-y-2">
            {judgment.riskWarnings.map((warning, i) => (
              <li
                key={i}
                className="text-sm text-[var(--text-secondary)] flex items-start gap-2"
              >
                <span className="text-[var(--neutral)]">•</span>
                {warning}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function VoteBar({
  position,
  votes,
  total,
}: {
  position: DebatePosition;
  votes: number;
  total: number;
}) {
  const config = positionConfig[position];
  const percentage = (votes / total) * 100;

  return (
    <div className="flex-1">
      <div className="flex items-center justify-between mb-2">
        <span className={cn("flex items-center gap-1", config.textColor)}>
          {config.icon && <config.icon className="w-3 h-3" />}
          <span className="text-xs font-mono">{config.label}</span>
        </span>
        <span className="text-[10px] font-mono text-[var(--text-muted)]">
          {votes}票
        </span>
      </div>
      <div className="h-2 bg-[var(--bg-primary)] rounded-full overflow-hidden">
        <div
          className={cn(
            "h-full rounded-full transition-all duration-500 bg-gradient-to-r",
            config.gradient,
          )}
          style={{
            width: `${percentage}%`,
            boxShadow: `0 0 10px ${config.glow}`,
          }}
        />
      </div>
    </div>
  );
}
