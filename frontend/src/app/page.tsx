"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import {
  ArrowUpRight,
  ChartNoAxesCombined,
  CircleDot,
  FileDown,
  FileText,
  Layers3,
  Loader2,
  Download,
} from "lucide-react";
import { useAnalysisStore } from "@/stores/analysis";
import { useAnalysisSession } from "@/hooks/useAnalysisSession";
import { useWeeklyPicks } from "@/hooks/useWeeklyPicks";
import { Button } from "@/components/common/Button";
import { AnalysisControls } from "@/components/analysis/AnalysisControls";
import { AnalysisProgress } from "@/components/analysis/AnalysisProgress";
import { MarketOverview } from "@/components/analysis/MarketOverview";
import { SentimentPanel } from "@/components/analysis/SentimentPanel";
import { TechnicalIndicatorsPanel } from "@/components/chart/TechnicalIndicators";
import { AnalysisResult } from "@/components/analysis/AnalysisResult";
import { DebateResult } from "@/components/analysis/DebateResult";
import { WeeklyValuePicks } from "@/components/analysis/WeeklyValuePicks";
import { AnalysisMode, StrategyType } from "@/types";
import type { AnalysisReport } from "@/types";
import {
  downloadReportJson,
  downloadReportMarkdown,
  downloadReportPdf,
} from "@/utils/reportExport";

const KLineChart = dynamic(
  () =>
    import("@/components/chart/KLineChart").then((module) => module.KLineChart),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[360px] items-center justify-center text-sm text-[var(--text-muted)]">
        <Loader2 className="mr-2 size-4 animate-spin" />
        正在加载图表...
      </div>
    ),
  },
);

export default function AnalysisPage() {
  const [strategy, setStrategy] = useState(StrategyType.BALANCED);
  const [mode, setMode] = useState(AnalysisMode.PIPELINE);
  const [isPdfGenerating, setIsPdfGenerating] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const { selectStock, start, stop } = useAnalysisSession();
  const weekly = useWeeklyPicks();
  const {
    activeMode,
    reportStrategy,
    reportMode,
    reportFinalSignal,
    currentStockCode,
    currentStockName,
    reportId,
    reportCreatedAt,
    isAnalyzing,
    currentStage,
    loadingMessage,
    marketData,
    technicalIndicators,
    sentimentData,
    judgment,
    confidence,
    finalSignal,
    error,
    debateViews,
  } = useAnalysisStore();

  const exportableReport: AnalysisReport | null =
    reportId &&
    reportCreatedAt &&
    currentStockCode &&
    currentStockName &&
    marketData &&
    technicalIndicators &&
    sentimentData &&
    finalSignal &&
    confidence
      ? {
          id: reportId,
          stockCode: currentStockCode,
          stockName: currentStockName,
          strategy: reportStrategy,
          mode: reportMode,
          createdAt: reportCreatedAt,
          marketData,
          technicalIndicators,
          sentimentData,
          tradeSignal: finalSignal,
          finalSignal: reportFinalSignal ?? finalSignal.type,
          confidence,
          judgment: judgment ?? undefined,
          debateViews: debateViews ?? undefined,
        }
      : null;

  const handlePdf = async () => {
    if (!exportableReport) return;
    setIsPdfGenerating(true);
    setExportError(null);
    try {
      await downloadReportPdf(exportableReport);
    } catch {
      setExportError("PDF 导出失败，请重试或选择其他格式");
    } finally {
      setIsPdfGenerating(false);
    }
  };

  return (
    <div className="space-y-6 px-4 py-6 sm:px-7 lg:px-9 lg:py-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-[11px] font-medium tracking-wide text-[var(--accent)]">
            <Layers3 className="size-3.5" />多 Agent 协作研究
          </p>
          <h1 className="text-[26px] font-semibold tracking-tight sm:text-3xl">
            智能分析
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--text-secondary)]">
            从市场信号到独立观点，让每一次决策更有依据。
          </p>
        </div>
        <span className="inline-flex items-center gap-2 rounded-full border border-[var(--border)] bg-white px-3.5 py-2 text-xs text-[var(--text-secondary)]">
          <CircleDot
            className={`size-3.5 ${isAnalyzing ? "animate-pulse text-[var(--accent)]" : "text-[var(--text-muted)]"}`}
          />
          {isAnalyzing
            ? "研究进行中"
            : reportId
              ? "报告已生成"
              : "等待开始研究"}
        </span>
      </header>

      <AnalysisControls
        stockCode={currentStockCode}
        stockName={currentStockName}
        marketData={marketData}
        strategy={strategy}
        mode={mode}
        isAnalyzing={isAnalyzing}
        onSelect={(stock) => selectStock(stock.code, stock.name)}
        onStrategyChange={setStrategy}
        onModeChange={setMode}
        onStart={() => {
          setExportError(null);
          start(strategy, mode);
        }}
        onStop={stop}
      />

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-[var(--bearish)]/20 bg-[var(--bearish-glow)] px-5 py-4 text-sm text-[var(--bearish)]"
        >
          {error}
        </div>
      )}

      {reportId && finalSignal && (
        <section className="glass-card p-5 sm:p-6 animate-enter">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="mb-1 text-[10px] font-medium tracking-wider text-[var(--accent)]">
                研究结论
              </p>
              <h2 className="text-lg font-semibold">分析结果</h2>
            </div>
            {exportableReport && (
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={isPdfGenerating}
                  onClick={() => void handlePdf()}
                >
                  {isPdfGenerating ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <FileText className="size-3.5" />
                  )}
                  导出PDF
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => downloadReportJson(exportableReport)}
                >
                  <Download className="size-3.5" />
                  导出JSON
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => downloadReportMarkdown(exportableReport)}
                >
                  <FileDown className="size-3.5" />
                  导出MD
                </Button>
              </div>
            )}
          </div>
          {exportError && (
            <p role="alert" className="mb-4 text-sm text-[var(--bearish)]">
              {exportError}
            </p>
          )}
          <AnalysisResult
            signal={finalSignal}
            confidence={confidence ?? judgment?.confidence ?? undefined}
          />
        </section>
      )}

      {marketData && <MarketOverview marketData={marketData} />}

      <div className="grid items-stretch gap-6 xl:grid-cols-[minmax(0,1fr)_290px]">
        <section className="glass-card min-w-0 p-5 sm:p-6">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold">K线走势</h2>
              <p className="mt-1.5 text-xs text-[var(--text-muted)]">
                价格、成交量与移动平均线
              </p>
            </div>
            <span className="rounded-md border border-[var(--border)] px-2.5 py-1 text-[10px] text-[var(--text-secondary)]">
              日 K · 前复权
            </span>
          </div>
          {marketData?.klineDates && marketData.klines ? (
            <KLineChart
              data={{
                dates: marketData.klineDates,
                klines: marketData.klines,
                volumes: marketData.klineVolumes || [],
                ma5: marketData.ma5 || [],
                ma10: marketData.ma10 || [],
                ma20: marketData.ma20 || [],
                ma60: marketData.ma60 || [],
              }}
              className="h-[360px]"
            />
          ) : (
            <div className="relative flex min-h-[330px] flex-col items-center justify-center overflow-hidden rounded-xl border border-dashed border-[var(--border)] bg-[#fafcfc] px-6 text-center sm:min-h-[360px]">
              <div
                aria-hidden="true"
                className="absolute inset-0 bg-[linear-gradient(#e5ecec66_1px,transparent_1px),linear-gradient(90deg,#e5ecec66_1px,transparent_1px)] bg-size-[48px_48px] mask-[linear-gradient(transparent,black,transparent)]"
              />
              <span className="relative mb-5 flex size-14 items-center justify-center rounded-2xl border border-[var(--border)] bg-white text-[var(--accent)]">
                <ChartNoAxesCombined className="size-6" strokeWidth={1.5} />
              </span>
              <p className="relative text-sm font-medium">
                {isAnalyzing
                  ? "正在获取市场数据"
                  : currentStockCode
                    ? "准备好，开始探索这只股票"
                    : "你的下一份研究，从这里开始"}
              </p>
              <p className="relative mt-2 max-w-64 text-xs leading-6 text-[var(--text-muted)]">
                {isAnalyzing
                  ? "行情到达后，将在这里呈现真实 K 线与成交量。"
                  : "选择一只股票并开始分析，查看行情走势、技术指标和多维研究结论。"}
              </p>
              {!currentStockCode && (
                <span className="relative mt-5 inline-flex items-center gap-1 text-xs text-[var(--accent)]">
                  在上方搜索研究标的
                  <ArrowUpRight className="size-3.5" />
                </span>
              )}
            </div>
          )}
        </section>
        <AnalysisProgress
          mode={activeMode ?? mode}
          stage={currentStage}
          isAnalyzing={isAnalyzing}
          hasReport={Boolean(reportId)}
          message={loadingMessage}
        />
      </div>

      {technicalIndicators && (
        <section className="glass-card p-5 sm:p-6">
          <h2 className="mb-5 text-lg font-semibold">技术指标</h2>
          <TechnicalIndicatorsPanel data={technicalIndicators} />
        </section>
      )}
      {sentimentData && <SentimentPanel sentimentData={sentimentData} />}
      {judgment && (
        <section className="glass-card p-5 sm:p-6">
          <h2 className="mb-5 text-lg font-semibold">辩论裁决</h2>
          <DebateResult
            judgment={judgment}
            debateViews={debateViews ?? undefined}
          />
        </section>
      )}

      <WeeklyValuePicks
        picks={weekly.picks}
        isLoading={weekly.isLoading}
        error={weekly.error}
        onRetry={weekly.retry}
        onSelect={(pick) => selectStock(pick.stockCode, pick.stockName)}
      />
      <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--border)] pt-5 text-[11px] leading-5 text-[var(--text-muted)]">
        <span>AlphaMind · 连接数据与观点</span>
        <span>数据来源与缓存状态随分析结果展示</span>
      </footer>
    </div>
  );
}
