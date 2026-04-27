"use client";

import { cn } from "@/utils";
import type { ConfidenceInterval } from "@/types";
import { ConfidenceLevel } from "@/types";

interface ConfidenceBarProps {
  confidence: ConfidenceInterval;
  showLabel?: boolean;
  className?: string;
}

const levelConfig = {
  [ConfidenceLevel.HIGH]: {
    gradient: "from-[var(--bullish)] to-[#00ff88]",
    glow: "var(--bullish-glow)",
    textColor: "text-[var(--bullish)]",
    label: "高置信度",
  },
  [ConfidenceLevel.MEDIUM]: {
    gradient: "from-[var(--neutral)] to-[#ffcc00]",
    glow: "var(--neutral-glow)",
    textColor: "text-[var(--neutral)]",
    label: "中置信度",
  },
  [ConfidenceLevel.LOW]: {
    gradient: "from-[var(--bearish)] to-[#ff6699]",
    glow: "var(--bearish-glow)",
    textColor: "text-[var(--bearish)]",
    label: "低置信度",
  },
};

export function ConfidenceBar({
  confidence,
  showLabel = true,
  className,
}: ConfidenceBarProps) {
  const config = levelConfig[confidence.level];
  const percentage = Math.round(confidence.value * 100);

  return (
    <div className={cn("space-y-2", className)}>
      {showLabel && (
        <div className="flex items-center justify-between text-sm">
          <span className={cn("font-medium font-mono", config.textColor)}>
            {config.label}
          </span>
          <span className="text-[var(--text-primary)] font-mono font-bold">
            {percentage}%
          </span>
        </div>
      )}
      <div className="h-2 bg-[var(--bg-tertiary)] rounded-full overflow-hidden">
        <div
          className={cn(
            "h-full rounded-full transition-all duration-500 relative overflow-hidden",
            `bg-gradient-to-r ${config.gradient}`,
          )}
          style={{
            width: `${percentage}%`,
            boxShadow: `0 0 10px ${config.glow}`,
          }}
        >
          {/* Shimmer effect */}
          <div
            className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent"
            style={{
              animation: "shimmer 2s infinite",
            }}
          />
        </div>
      </div>
      {confidence.explanation && (
        <p className="text-xs text-[var(--text-muted)] font-mono">
          {confidence.explanation}
        </p>
      )}
      <style jsx>{`
        @keyframes shimmer {
          0% {
            transform: translateX(-100%);
          }
          100% {
            transform: translateX(100%);
          }
        }
      `}</style>
    </div>
  );
}
