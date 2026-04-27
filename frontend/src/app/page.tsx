"use client";

import { useState, useRef } from "react";
import { useAnalysisStore } from "@/stores/analysis";
import { StockSearch } from "@/components/common/StockSearch";
import { Button } from "@/components/common/Button";
import { KLineChart } from "@/components/chart/KLineChart";
import { TechnicalIndicatorsPanel } from "@/components/chart/TechnicalIndicators";
import { AnalysisResult } from "@/components/analysis/AnalysisResult";
import { DebateResult } from "@/components/analysis/DebateResult";
import type { StockSearchResult, StrategyType } from "@/types";
import { StrategyType as ST, ConfidenceLevel } from "@/types";
import { formatDate } from "@/utils";
import { Play, Square, Loader2, TrendingUp, TrendingDown } from "lucide-react";

export default function AnalysisPage() {
  const [strategy, setStrategy] = useState<StrategyType>(ST.BALANCED);
  const eventSourceRef = useRef<EventSource | null>(null);

  const {
    currentStockCode,
    currentStockName,
    isAnalyzing,
    currentStage,
    loadingMessage,
    marketData,
    technicalIndicators,
    judgment,
    finalSignal,
    error,
    setCurrentStock,
    setIsAnalyzing,
    setCurrentStage,
    setMarketData,
    setTechnicalIndicators,
    setJudgment,
    setFinalSignal,
    setError,
    reset,
    handleSSEEvent,
  } = useAnalysisStore();

  const handleStockSelect = (stock: StockSearchResult) => {
    setCurrentStock(stock.code, stock.name);
  };

  const handleStartAnalysis = () => {
    if (!currentStockCode) return;

    reset();
    setIsAnalyzing(true);
    setCurrentStage("START", "正在连接分析服务...");

    const url = `/api/v1/analysis/stream?stockCode=${encodeURIComponent(currentStockCode)}&strategy=${strategy}&enableDebate=true`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    const eventTypes = ["stage", "data", "complete", "error"];
    eventTypes.forEach((type) => {
      es.addEventListener(type, (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          // 确保 event 字段正确（有些后端直接在 data 中携带 event，也可能需要合并）
          handleSSEEvent({ event: type as "stage" | "data" | "complete" | "error", ...data });
          if (type === "complete" || type === "error") {
            es.close();
            eventSourceRef.current = null;
            setIsAnalyzing(false);
          }
        } catch {
          // ignore parse errors
        }
      });
    });

    es.onerror = () => {
      setError("连接分析服务失败，请检查后端是否启动");
      setIsAnalyzing(false);
      es.close();
      eventSourceRef.current = null;
    };
  };

  const handleStopAnalysis = () => {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    reset();
    setIsAnalyzing(false);
  };

  const isPositive = (marketData?.changePercent || 0) >= 0;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between animate-enter">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="gradient-text">智能分析</span>
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-1 font-mono">
            MULTI-AGENT STOCK ANALYSIS ENGINE
          </p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 glass-card">
          <div className="w-2 h-2 bg-[var(--bullish)] rounded-full pulse-live" />
          <span className="text-xs font-mono text-[var(--text-secondary)]">
            实时行情
          </span>
        </div>
      </div>

      {/* Search & Control Bar */}
      <div className="glass-card-glow p-5 animate-enter delay-100">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1">
            <StockSearch
              onSelect={handleStockSelect}
              placeholder="搜索股票代码或名称..."
            />
          </div>

          {currentStockCode && (
            <div className="flex items-center gap-4">
              {/* Stock Info */}
              <div className="flex items-center gap-4 px-4 py-2 bg-[var(--bg-tertiary)] rounded-xl">
                <div className="text-right">
                  <div className="font-bold text-[var(--text-primary)] text-lg">
                    {currentStockName}
                  </div>
                  <div className="text-xs text-[var(--text-muted)] font-mono">
                    {currentStockCode}
                  </div>
                </div>
                <div className="h-10 w-px bg-[var(--border)]" />
                <div className="text-right">
                  <div className="font-mono font-bold text-xl">
                    ¥{marketData?.currentPrice?.toFixed(2) || "--"}
                  </div>
                  <div
                    className={`flex items-center justify-end gap-1 text-sm font-mono ${isPositive ? "text-[var(--bullish)]" : "text-[var(--bearish)]"}`}
                  >
                    {isPositive ? (
                      <TrendingUp className="w-4 h-4" />
                    ) : (
                      <TrendingDown className="w-4 h-4" />
                    )}
                    {marketData?.changePercent
                      ? `${isPositive ? "+" : ""}${marketData.changePercent.toFixed(2)}%`
                      : "--"}
                  </div>
                </div>
              </div>

              <div className="h-10 w-px bg-[var(--border)]" />

              {/* Strategy Selector */}
              <select
                value={strategy}
                onChange={(e) => setStrategy(e.target.value as StrategyType)}
                className="h-11 px-4 rounded-xl bg-[var(--bg-tertiary)] border border-[var(--border)] text-sm font-mono focus:outline-none focus:border-[var(--accent)] transition-colors cursor-pointer"
              >
                <option value={ST.CONSERVATIVE}>🎯 保守策略</option>
                <option value={ST.BALANCED}>⚖️ 平衡策略</option>
                <option value={ST.AGGRESSIVE}>🚀 激进策略</option>
              </select>

              {/* Action Button */}
              {isAnalyzing ? (
                <Button
                  variant="destructive"
                  onClick={handleStopAnalysis}
                  className="h-11 px-6 bg-[var(--bearish)] hover:bg-[var(--bearish)]/80 btn-glow"
                >
                  <Square className="w-4 h-4" />
                  停止分析
                </Button>
              ) : (
                <Button
                  onClick={handleStartAnalysis}
                  disabled={!currentStockCode}
                  className="h-11 px-6 bg-gradient-to-r from-[var(--accent)] to-[#00ff88] text-[var(--bg-primary)] font-semibold btn-glow"
                >
                  <Play className="w-4 h-4" />
                  开始分析
                </Button>
              )}
            </div>
          )}
        </div>

        {/* Progress Bar */}
        {isAnalyzing && (
          <div className="mt-5 p-4 bg-[var(--bg-tertiary)] rounded-xl">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <Loader2 className="w-5 h-5 text-[var(--accent)] animate-spin" />
                <span className="text-sm font-medium text-[var(--text-primary)]">
                  {loadingMessage}
                </span>
              </div>
              <span className="text-xs font-mono text-[var(--text-muted)]">
                {currentStage}
              </span>
            </div>
            <div className="flex gap-1.5">
              {[
                "START",
                "MARKET",
                "TECHNICAL",
                "SENTIMENT",
                "PORTFOLIO",
                "DEBATE",
              ].map((stage, i) => {
                const stages = [
                  "START",
                  "MARKET",
                  "TECHNICAL",
                  "SENTIMENT",
                  "PORTFOLIO",
                  "DEBATE",
                ];
                const currentIndex = stages.indexOf(currentStage);
                const isComplete = currentIndex >= i;
                const isActive = currentIndex === i;

                return (
                  <div key={stage} className="flex-1">
                    <div
                      className={`h-1.5 rounded-full transition-all duration-500 ${
                        isComplete
                          ? "bg-gradient-to-r from-[var(--accent)] to-[#00ff88]"
                          : "bg-[var(--bg-primary)]"
                      }`}
                      style={{
                        boxShadow: isActive
                          ? "0 0 10px var(--accent-glow)"
                          : "none",
                      }}
                    />
                    <div
                      className={`text-[10px] font-mono mt-1 ${isComplete ? "text-[var(--accent)]" : "text-[var(--text-muted)]"}`}
                    >
                      {stage}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {error && (
          <div className="mt-4 p-4 bg-[var(--bearish-glow)] border border-[var(--bearish)]/30 rounded-xl text-[var(--bearish)] text-sm">
            {error}
          </div>
        )}
      </div>

      {/* Market Data */}
      {marketData && (
        <div className="glass-card p-5 animate-enter delay-200">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <span className="w-1.5 h-5 bg-[var(--accent)] rounded-full" />
              市场行情
            </h2>
            <span className="text-xs text-[var(--text-muted)] font-mono">
              {formatDate(marketData.updateTime)}
            </span>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <MarketStat label="当前价" value={marketData.currentPrice} />
            <MarketStat
              label="涨跌幅"
              value={marketData.changePercent}
              format="percent"
              color
            />
            <MarketStat
              label="成交量"
              value={marketData.volume}
              format="amount"
            />
            <MarketStat
              label="换手率"
              value={marketData.turnoverRate}
              format="percent"
            />
            <MarketStat
              label="市值"
              value={marketData.marketCap}
              format="marketCap"
            />
            <MarketStat label="市盈率" value={marketData.pe} />
            <MarketStat label="市净率" value={marketData.pb} />
            <MarketStat label="52周高" value={marketData.high} />
          </div>
        </div>
      )}

      {/* K线图和技术指标 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass-card p-5 animate-enter delay-300">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--accent)] rounded-full" />
            K线走势
          </h2>
          <div className="chart-container rounded-xl overflow-hidden">
            <KLineChart
              data={{
                dates: [
                  "2026-04-01",
                  "2026-04-02",
                  "2026-04-03",
                  "2026-04-04",
                  "2026-04-07",
                  "2026-04-08",
                  "2026-04-09",
                  "2026-04-10",
                  "2026-04-11",
                  "2026-04-14",
                ],
                klines: [
                  [100, 102, 99, 103],
                  [103, 105, 102, 106],
                  [106, 104, 103, 107],
                  [107, 108, 106, 109],
                  [109, 110, 108, 111],
                  [111, 109, 108, 112],
                  [112, 115, 111, 116],
                  [116, 114, 113, 117],
                  [117, 118, 116, 119],
                  [119, 120, 118, 121],
                ] as [number, number, number, number][],
                volumes: [
                  1000, 1200, 1100, 1300, 1400, 1200, 1500, 1400, 1600, 1700,
                ],
                ma5: [101, 103, 104, 105, 107, 108, 110, 112, 114, 116],
                ma10: [100, 101, 102, 103, 104, 105, 106, 107, 108, 109],
                ma20: [98, 99, 99, 100, 101, 102, 102, 103, 104, 105],
                ma60: [95, 95, 96, 96, 97, 97, 98, 98, 99, 99],
              }}
              className="h-[400px]"
            />
          </div>
        </div>

        <div className="glass-card p-5 animate-enter delay-400">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--neutral)] rounded-full" />
            技术指标
          </h2>
          {technicalIndicators ? (
            <TechnicalIndicatorsPanel data={technicalIndicators} />
          ) : (
            <div className="text-center text-[var(--text-muted)] py-12 font-mono">
              暂无数据
            </div>
          )}
        </div>
      </div>

      {/* Analysis Results */}
      {finalSignal && (
        <div className="glass-card p-5 animate-enter delay-300">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--bullish)] rounded-full" />
            分析结果
          </h2>
          <AnalysisResult
            signal={finalSignal}
            confidence={{ value: 0.75, level: ConfidenceLevel.MEDIUM }}
          />
        </div>
      )}

      {/* Debate Results */}
      {judgment && (
        <div className="glass-card p-5 animate-enter delay-400">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--accent)] rounded-full" />
            辩论裁决
          </h2>
          <DebateResult judgment={judgment} />
        </div>
      )}
    </div>
  );
}

function MarketStat({
  label,
  value,
  format,
  color,
}: {
  label: string;
  value: number;
  format?: "percent" | "amount" | "marketCap";
  color?: boolean;
}) {
  const formatValue = () => {
    if (format === "percent")
      return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
    if (format === "amount")
      return value >= 1e8
        ? `${(value / 1e8).toFixed(2)}亿`
        : `${(value / 1e4).toFixed(2)}万`;
    if (format === "marketCap")
      return value >= 1e12
        ? `${(value / 1e12).toFixed(2)}万亿`
        : `${(value / 1e8).toFixed(2)}亿`;
    return value.toFixed(2);
  };

  const getColorClass = () => {
    if (!color) return "text-[var(--text-primary)]";
    return value >= 0 ? "text-[var(--bullish)]" : "text-[var(--bearish)]";
  };

  return (
    <div className="p-3 bg-[var(--bg-tertiary)] rounded-xl">
      <div className="text-xs text-[var(--text-muted)] mb-1 font-mono">
        {label}
      </div>
      <div className={`text-lg font-bold font-mono ${getColorClass()}`}>
        {formatValue()}
      </div>
    </div>
  );
}
