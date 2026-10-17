import {
  Play,
  Square,
  SlidersHorizontal,
  ArrowUpRight,
  ArrowDownRight,
} from "lucide-react";
import { StockSearch } from "@/components/common/StockSearch";
import { Button } from "@/components/common/Button";
import Link from "next/link";
import { AnalysisMode, StrategyType } from "@/types";
import type { MarketData, StockSearchResult } from "@/types";

interface AnalysisControlsProps {
  stockCode: string | null;
  stockName: string | null;
  marketData: MarketData | null;
  strategy: StrategyType;
  mode: AnalysisMode;
  isAnalyzing: boolean;
  onSelect: (stock: StockSearchResult) => void;
  onStrategyChange: (strategy: StrategyType) => void;
  onModeChange: (mode: AnalysisMode) => void;
  onStart: () => void;
  onStop: () => void;
}

export function AnalysisControls(props: AnalysisControlsProps) {
  const { stockCode, stockName, marketData, strategy, mode, isAnalyzing } =
    props;
  const change = marketData?.changePercent;
  return (
    <section
      aria-label="分析设置"
      className="relative z-30 glass-card p-5 sm:p-6"
    >
      <div className="mb-5 flex items-center gap-2 text-xs font-medium text-[var(--text-secondary)]">
        <SlidersHorizontal className="size-4 text-[var(--accent)]" />
        研究标的与分析设置
      </div>
      <div className="grid gap-4 xl:grid-cols-[minmax(230px,1fr)_minmax(0,1.25fr)]">
        <div className="min-w-0">
          <label
            htmlFor="analysis-stock-search"
            className="mb-2 block text-xs text-[var(--text-muted)]"
          >
            选择股票
          </label>
          <StockSearch
            id="analysis-stock-search"
            onSelect={props.onSelect}
            placeholder="搜索股票代码或名称..."
          />
        </div>
        <div className="grid grid-cols-2 items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
          <label className="min-w-0 text-xs text-[var(--text-muted)]">
            投资策略
            <select
              aria-label="投资策略"
              value={strategy}
              disabled={isAnalyzing}
              onChange={(event) =>
                props.onStrategyChange(event.target.value as StrategyType)
              }
              className="mt-2 h-12 w-full rounded-xl border border-[var(--border)] bg-white px-3 text-sm text-[var(--text-primary)] disabled:opacity-60"
            >
              <option value={StrategyType.CONSERVATIVE}>保守策略</option>
              <option value={StrategyType.BALANCED}>平衡策略</option>
              <option value={StrategyType.AGGRESSIVE}>激进策略</option>
            </select>
          </label>
          <label className="min-w-0 text-xs text-[var(--text-muted)]">
            分析模式
            <select
              aria-label="分析模式"
              value={mode}
              disabled={isAnalyzing}
              onChange={(event) =>
                props.onModeChange(event.target.value as AnalysisMode)
              }
              className="mt-2 h-12 w-full rounded-xl border border-[var(--border)] bg-white px-3 text-sm text-[var(--text-primary)] disabled:opacity-60"
            >
              <option value={AnalysisMode.PIPELINE}>流水线模式</option>
              <option value={AnalysisMode.DEBATE}>辩论模式</option>
            </select>
          </label>
          <Button
            variant={isAnalyzing ? "destructive" : "default"}
            disabled={!stockCode}
            onClick={isAnalyzing ? props.onStop : props.onStart}
            className="col-span-2 h-12 px-5 sm:col-span-1"
          >
            {isAnalyzing ? (
              <Square className="size-4" />
            ) : (
              <Play className="size-4" />
            )}
            {isAnalyzing ? "停止分析" : "开始分析"}
          </Button>
        </div>
      </div>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border)] pt-4">
        {stockCode ? (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="font-semibold">{stockName}</span>
            <span className="font-mono text-xs text-[var(--text-muted)]">
              {stockCode}
            </span>
            {marketData && (
              <span className="font-mono font-semibold">
                ¥{marketData.currentPrice.toFixed(2)}
              </span>
            )}
            {change != null && (
              <span
                className={`inline-flex items-center font-mono text-xs ${change >= 0 ? "text-[var(--bullish)]" : "text-[var(--bearish)]"}`}
              >
                {change >= 0 ? (
                  <ArrowUpRight className="size-3.5" />
                ) : (
                  <ArrowDownRight className="size-3.5" />
                )}
                {change >= 0 ? "+" : ""}
                {change.toFixed(2)}%
              </span>
            )}
          </div>
        ) : (
          <p className="text-xs text-[var(--text-muted)]">
            输入代码、名称或行业，开始一份新的研究。
          </p>
        )}
        <span className="text-xs text-[var(--text-muted)]">
          {mode === AnalysisMode.DEBATE
            ? "四阶段分析 + 多空辩论与仲裁"
            : "行情 → 技术 → 舆情 → 综合决策"}
        </span>
      </div>
      <Link
        href={`/investment-model${stockCode ? `?stock=${encodeURIComponent(stockCode)}` : ""}`}
        className="mt-4 inline-flex text-xs font-medium text-[var(--accent)]"
      >
        使用我的投资模型复核 →
      </Link>
    </section>
  );
}
