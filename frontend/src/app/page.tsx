"use client";

import { useState, useRef, useEffect } from "react";
import { useAnalysisStore } from "@/stores/analysis";
import { useSSE } from "@/hooks/useSSE";
import { StockSearch } from "@/components/common/StockSearch";
import { Button } from "@/components/common/Button";
import { KLineChart } from "@/components/chart/KLineChart";
import { TechnicalIndicatorsPanel } from "@/components/chart/TechnicalIndicators";
import { AnalysisResult } from "@/components/analysis/AnalysisResult";
import { DebateResult } from "@/components/analysis/DebateResult";
import { WeeklyValuePicks } from "@/components/analysis/WeeklyValuePicks";
import { getWeeklyValuePicks } from "@/api/client";
import type {
  AnalysisMode as AnalysisModeType,
  AnalysisReport,
  StockSearchResult,
  StrategyType,
  WeeklyStockRecommendation,
} from "@/types";
import {
  AnalysisMode,
  StrategyType as ST,
  ConfidenceLevel,
  DebatePosition,
} from "@/types";
import { formatDate } from "@/utils";
import { downloadReportJson, downloadReportMarkdown, downloadReportPdf } from "@/utils/reportExport";
import {
  Play,
  Square,
  Loader2,
  TrendingUp,
  TrendingDown,
  ThumbsUp,
  ThumbsDown,
  Newspaper,
  Download,
  FileDown,
  FileText,
} from "lucide-react";

export default function AnalysisPage() {
  const [strategy, setStrategy] = useState<StrategyType>(ST.BALANCED);
  const [analysisMode, setAnalysisMode] = useState<AnalysisModeType>(AnalysisMode.PIPELINE);
  const [weeklyPicks, setWeeklyPicks] = useState<WeeklyStockRecommendation[]>([]);
  const [weeklyPicksLoading, setWeeklyPicksLoading] = useState(true);
  const [weeklyPicksError, setWeeklyPicksError] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const { connect: connectSSE, disconnect: disconnectSSE } = useSSE();

  // 组件卸载时关闭 SSE 连接
  useEffect(() => {
    return () => disconnectSSE();
  }, [disconnectSSE]);

  useEffect(() => {
    let cancelled = false;

    const loadWeeklyPicks = async () => {
      setWeeklyPicksLoading(true);
      try {
        const data = await getWeeklyValuePicks();
        if (!cancelled) {
          setWeeklyPicks(data);
          setWeeklyPicksError(null);
        }
      } catch (err) {
        console.error("Weekly picks load error:", err);
        if (!cancelled) {
          setWeeklyPicksError("本周推荐加载失败，请稍后再试");
        }
      } finally {
        if (!cancelled) {
          setWeeklyPicksLoading(false);
        }
      }
    };

    void loadWeeklyPicks();

    return () => {
      cancelled = true;
    };
  }, []);

  const {
    currentStockCode,
    currentStockName,
    isAnalyzing,
    currentStage,
    loadingMessage,
    marketData,
    technicalIndicators,
    sentimentData,
    judgment,
    finalSignal,
    error,
    setCurrentStock,
    setIsAnalyzing,
    setCurrentStage,
    setMarketData,
    setTechnicalIndicators,
    setSentimentData,
    setJudgment,
    setFinalSignal,
    setError,
    reset,
    handleSSEEvent,
    debateViews,
    setDebateViews,
  } = useAnalysisStore();

  const handleStockSelect = (stock: StockSearchResult) => {
    setCurrentStock(stock.code, stock.name);
  };

  const handleWeeklyPickSelect = (pick: WeeklyStockRecommendation) => {
    setCurrentStock(pick.stockCode, pick.stockName);
  };

  const handleStartAnalysis = () => {
    if (!currentStockCode) return;

    reset();
    setIsAnalyzing(true);
    setCurrentStage("START", "正在连接分析服务...");

    const url = `/api/v1/analysis/stream?stockCode=${encodeURIComponent(currentStockCode)}&strategy=${strategy}&mode=${analysisMode}`;

    const es = connectSSE(url, {
      onMessage: (eventType, data) => {
        const payload = data as Record<string, unknown>;
        if (eventType === "result") {
          const report = (payload?.data ?? payload) as Record<string, unknown>;
          if (report) {
            if (report.marketData) setMarketData(report.marketData as Parameters<typeof setMarketData>[0]);
            if (report.technicalIndicators) setTechnicalIndicators(report.technicalIndicators as Parameters<typeof setTechnicalIndicators>[0]);
            if (report.sentimentData) setSentimentData(report.sentimentData as Parameters<typeof setSentimentData>[0]);
            if (report.tradeSignal) setFinalSignal(report.tradeSignal as Parameters<typeof setFinalSignal>[0]);
            if (report.judgment) setJudgment(report.judgment as Parameters<typeof setJudgment>[0]);
            setDebateViews((report.debateViews as Parameters<typeof setDebateViews>[0]) ?? null);
          }
          setIsAnalyzing(false);
          disconnectSSE();
        } else if (eventType === "complete") {
          setIsAnalyzing(false);
          disconnectSSE();
        } else if (eventType === "error") {
          setError((payload?.message as string) || "分析失败");
          setIsAnalyzing(false);
          disconnectSSE();
        } else {
          handleSSEEvent({ event: eventType as "stage" | "data", ...payload });
        }
      },
      onError: () => {
        setError("连接分析服务失败，请检查后端是否启动");
        setIsAnalyzing(false);
      },
    });
    eventSourceRef.current = es;
  };

  const handleStopAnalysis = () => {
    disconnectSSE();
    eventSourceRef.current = null;
    reset();
    setIsAnalyzing(false);
  };

  const [isPdfGenerating, setIsPdfGenerating] = useState(false);
  const handleDownloadPdf = async (report: AnalysisReport) => {
    setIsPdfGenerating(true);
    try {
      await downloadReportPdf(report);
    } finally {
      setIsPdfGenerating(false);
    }
  };

  const isPositive = (marketData?.changePercent || 0) >= 0;

  const exportableReport: AnalysisReport | null =
    currentStockCode &&
    currentStockName &&
    marketData &&
    technicalIndicators &&
    sentimentData &&
    finalSignal
      ? {
          id: crypto.randomUUID(),
          stockCode: currentStockCode,
          stockName: currentStockName,
          finalSignal: finalSignal.type,
          confidence:
            judgment?.confidence || {
              value: 0.65,
              level: ConfidenceLevel.MEDIUM,
            },
          tradeSignal: finalSignal,
          marketData,
          technicalIndicators,
          sentimentData,
          judgment: judgment || {
            finalPosition: DebatePosition.NEUTRAL,
            confidence: {
              value: 0.65,
              level: ConfidenceLevel.MEDIUM,
            },
            reasoning: "未启用辩论模式，使用流水线综合结论。",
            voteBreakdown: {
              [DebatePosition.BULLISH]: 0,
              [DebatePosition.BEARISH]: 0,
              [DebatePosition.NEUTRAL]: 1,
            },
            riskWarnings: [],
            finalSignal,
          },
          debateViews: debateViews ?? undefined,
          createdAt: new Date().toISOString(),
        }
      : null;

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

      <WeeklyValuePicks
        picks={weeklyPicks}
        isLoading={weeklyPicksLoading}
        error={weeklyPicksError}
        onSelect={handleWeeklyPickSelect}
      />

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

              {/* Analysis Mode Selector */}
              <select
                value={analysisMode}
                onChange={(e) => setAnalysisMode(e.target.value as AnalysisModeType)}
                className="h-11 px-4 rounded-xl bg-[var(--bg-tertiary)] border border-[var(--border)] text-sm font-mono focus:outline-none focus:border-[var(--accent)] transition-colors cursor-pointer"
              >
                <option value={AnalysisMode.PIPELINE}>🔄 流水线模式</option>
                <option value={AnalysisMode.DEBATE}>⚔️ 辩论模式</option>
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

      {/* 舆情分析 */}
      {sentimentData && (
        <div className="glass-card p-5 animate-enter delay-250">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--neutral)] rounded-full" />
            舆情分析
          </h2>
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-4 p-3 bg-[var(--bg-tertiary)] rounded-xl">
              <div className="flex items-center gap-2">
                <Newspaper className="w-4 h-4 text-[var(--text-muted)]" />
                <span className="text-xs font-mono text-[var(--text-muted)]">综合评分</span>
              </div>
              <div className="text-2xl font-bold font-mono">
                {Math.round(sentimentData.sentimentScore * 100)}
                <span className="text-sm font-normal text-[var(--text-muted)]">/100</span>
              </div>
              <span className="ml-auto text-sm font-mono text-[var(--text-secondary)]">
                {sentimentData.sentimentTrend}
              </span>
              <span className="text-xs font-mono text-[var(--text-muted)]">
                媒体关注度 {Math.round(sentimentData.mediaAttention * 100)}%
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {sentimentData.positiveFactors?.length > 0 && (
                <div>
                  <div className="text-xs font-mono text-[var(--bullish)] mb-2 flex items-center gap-1">
                    <ThumbsUp className="w-3 h-3" />
                    利好因素
                  </div>
                  <ul className="space-y-1">
                    {sentimentData.positiveFactors.map((f, i) => (
                      <li key={i} className="text-sm text-[var(--text-secondary)] flex gap-2">
                        <span className="text-[var(--bullish)] shrink-0">+</span>
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {sentimentData.negativeFactors?.length > 0 && (
                <div>
                  <div className="text-xs font-mono text-[var(--bearish)] mb-2 flex items-center gap-1">
                    <ThumbsDown className="w-3 h-3" />
                    风险因素
                  </div>
                  <ul className="space-y-1">
                    {sentimentData.negativeFactors.map((f, i) => (
                      <li key={i} className="text-sm text-[var(--text-secondary)] flex gap-2">
                        <span className="text-[var(--bearish)] shrink-0">-</span>
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {sentimentData.analysisSummary && (
              <p className="text-sm text-[var(--text-muted)] font-mono border-t border-[var(--border)] pt-3 leading-relaxed">
                {sentimentData.analysisSummary}
              </p>
            )}
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
            {marketData?.klineDates && marketData.klines ? (
              <KLineChart
                data={{
                  dates: marketData.klineDates,
                  klines: marketData.klines,
                  volumes: marketData.klineVolumes || [],
                  ma5: (marketData.ma5 || []).map((v) => v ?? 0),
                  ma10: (marketData.ma10 || []).map((v) => v ?? 0),
                  ma20: (marketData.ma20 || []).map((v) => v ?? 0),
                  ma60: (marketData.ma60 || []).map((v) => v ?? 0),
                }}
                className="h-[400px]"
              />
            ) : (
              <div className="h-[400px] flex items-center justify-center text-[var(--text-muted)] font-mono">
                {currentStockCode ? "等待分析获取K线数据..." : "请先选择股票"}
              </div>
            )}
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
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <span className="w-1.5 h-5 bg-[var(--bullish)] rounded-full" />
              分析结果
            </h2>
            {exportableReport && (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="border-[var(--border)]"
                  disabled={isPdfGenerating}
                  onClick={() => void handleDownloadPdf(exportableReport)}
                >
                  {isPdfGenerating ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <FileText className="w-4 h-4" />
                  )}
                  导出PDF
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="border-[var(--border)]"
                  onClick={() => downloadReportJson(exportableReport)}
                >
                  <Download className="w-4 h-4" />
                  导出JSON
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="border-[var(--border)]"
                  onClick={() => downloadReportMarkdown(exportableReport)}
                >
                  <FileDown className="w-4 h-4" />
                  导出MD
                </Button>
              </div>
            )}
          </div>
          <AnalysisResult
            signal={finalSignal}
            confidence={
              judgment?.confidence || { value: 0.65, level: ConfidenceLevel.MEDIUM }
            }
          />
        </div>
      )}

      {/* Debate Results */}
      {analysisMode === AnalysisMode.DEBATE && judgment && (
        <div className="glass-card p-5 animate-enter delay-400">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <span className="w-1.5 h-5 bg-[var(--accent)] rounded-full" />
            辩论裁决
          </h2>
          <DebateResult judgment={judgment} debateViews={debateViews ?? undefined} />
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
