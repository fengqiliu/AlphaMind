"use client";

import { confidenceTitle, confidenceValue } from "@/utils/confidenceMetadata";
import { useEffect, useRef, useState } from "react";
import {
  History,
  FileText,
  Download,
  FileDown,
  Loader2,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  X,
  Search,
} from "lucide-react";
import { Button } from "@/components/common/Button";
import { ReportDetail } from "@/components/analysis/ReportDetail";
import { getAnalysisHistory, getAnalysisReport } from "@/api/client";
import type { AnalysisReport } from "@/types";
import { SignalType } from "@/types";
import { cn, formatDate } from "@/utils";
import { modeLabel, strategyLabel } from "@/utils/reportMetadata";
import {
  downloadReportJson,
  downloadReportMarkdown,
  downloadReportPdf,
} from "@/utils/reportExport";

const SIGNALS = {
  [SignalType.BUY]: {
    label: "买入",
    style: "signal-bullish",
    icon: ArrowUpRight,
  },
  [SignalType.SELL]: {
    label: "卖出",
    style: "signal-bearish",
    icon: ArrowDownRight,
  },
  [SignalType.HOLD]: { label: "持有", style: "signal-neutral", icon: Minus },
};

export default function HistoryPage() {
  const [reports, setReports] = useState<AnalysisReport[]>([]);
  const [selectedReport, setSelectedReport] = useState<AnalysisReport | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [isPdfGenerating, setIsPdfGenerating] = useState(false);
  const detailRef = useRef<HTMLElement>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      // 列表与链接中的单份报告独立加载，旧报告不受最近列表数量限制。
      const reportId = new URL(window.location.href).searchParams.get("report");
      const historyPromise = getAnalysisHistory()
        .then((items) => {
          if (!cancelled) setReports(items);
        })
        .catch(() => {
          if (!cancelled) setError("加载历史记录失败");
        });
      const detailPromise = reportId
        ? getAnalysisReport(reportId)
            .then((report) => {
              if (!cancelled) setSelectedReport(report);
            })
            .catch((failure: Error) => {
              if (!cancelled) setDetailError(failure.message || "报告加载失败");
            })
        : Promise.resolve();
      await Promise.all([historyPromise, detailPromise]);
      if (!cancelled) setIsLoading(false);
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const selectReport = (report: AnalysisReport | null) => {
    setSelectedReport(report);
    setDetailError(null);
    const url = new URL(window.location.href);
    if (report) url.searchParams.set("report", report.id);
    else url.searchParams.delete("report");
    window.history.replaceState(null, "", `${url.pathname}${url.search}`);
  };

  useEffect(() => {
    if (selectedReport) {
      detailRef.current?.focus({ preventScroll: true });
      if (window.matchMedia("(max-width: 1279px)").matches) {
        detailRef.current?.scrollIntoView({
          block: "start",
          behavior: "instant",
        });
      }
    }
  }, [selectedReport]);

  const retry = () => {
    setError(null);
    setDetailError(null);
    setIsLoading(true);
    setAttempt((value) => value + 1);
  };
  const handlePdf = async () => {
    if (!selectedReport) return;
    setIsPdfGenerating(true);
    setDetailError(null);
    try {
      await downloadReportPdf(selectedReport);
    } catch (error) {
      console.error("PDF 导出失败", error);
      setDetailError("PDF 导出失败，请稍后重试");
    } finally {
      setIsPdfGenerating(false);
    }
  };
  const filtered = reports.filter((report) =>
    `${report.stockName} ${report.stockCode}`
      .toLowerCase()
      .includes(query.trim().toLowerCase()),
  );
  const canExportDocument =
    selectedReport?.personalModel ||
    (selectedReport?.marketData &&
      selectedReport?.technicalIndicators &&
      selectedReport?.sentimentData &&
      selectedReport?.tradeSignal &&
      selectedReport?.confidence);

  return (
    <div className="space-y-6 px-4 py-6 sm:px-7 lg:px-9 lg:py-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="mb-2 text-[11px] font-medium text-[var(--accent)]">
            研究档案
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">历史记录</h1>
          <p className="mt-2 text-sm text-[var(--text-secondary)]">
            回到当时的判断，查看完整依据与多空观点。
          </p>
        </div>
        <span className="rounded-full border border-[var(--border)] bg-white px-4 py-2 text-xs text-[var(--text-secondary)]">
          {reports.length} 条记录
        </span>
      </header>
      <div
        className={cn(
          "grid items-start gap-6",
          selectedReport && "xl:grid-cols-[320px_minmax(0,1fr)]",
        )}
      >
        <section
          aria-label="分析历史"
          className="glass-card min-w-0 overflow-hidden"
        >
          <div className="border-b border-[var(--border)] p-5">
            <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
              <History className="size-4 text-[var(--accent)]" />
              分析历史
            </h2>
            <label className="flex items-center gap-2 rounded-xl bg-[var(--bg-tertiary)] px-3 py-2.5">
              <Search className="size-4 shrink-0 text-[var(--text-muted)]" />
              <input
                aria-label="筛选历史股票"
                placeholder="筛选代码或名称"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="min-w-0 w-full bg-transparent text-sm outline-none"
              />
            </label>
          </div>
          {isLoading ? (
            <div
              role="status"
              className="flex items-center justify-center gap-2 p-10 text-sm text-[var(--text-muted)]"
            >
              <Loader2 className="size-4 animate-spin" />
              正在加载历史...
            </div>
          ) : error ? (
            <div
              role="alert"
              className="space-y-3 p-6 text-sm text-[var(--bearish)]"
            >
              <p>{error}</p>
              <Button variant="outline" size="sm" onClick={retry}>
                重新加载
              </Button>
            </div>
          ) : reports.length === 0 ? (
            <div className="p-10 text-center">
              <FileText className="mx-auto mb-4 size-8 text-[var(--text-muted)]" />
              <p className="text-sm">暂无分析记录</p>
              <p className="mt-2 text-xs text-[var(--text-muted)]">
                开始分析股票后将自动保存历史
              </p>
            </div>
          ) : filtered.length === 0 ? (
            <p className="p-6 text-sm text-[var(--text-muted)]">
              未找到匹配的历史记录
            </p>
          ) : (
            <ul className="divide-y divide-[var(--border)] xl:max-h-[75vh] xl:overflow-auto">
              {filtered.map((report) => {
                const signal = SIGNALS[report.finalSignal];
                const Icon = signal?.icon ?? Minus;
                return (
                  <li key={report.id}>
                    <button
                      type="button"
                      aria-label={`查看报告 ${report.stockName} ${report.stockCode} ${report.id}`}
                      aria-pressed={selectedReport?.id === report.id}
                      onClick={() => selectReport(report)}
                      className={cn(
                        "w-full p-5 text-left transition-colors hover:bg-[var(--bg-tertiary)]",
                        selectedReport?.id === report.id &&
                          "bg-[var(--accent-subtle)]",
                      )}
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold">
                          {report.stockName}
                        </span>
                        <span className="font-mono text-xs text-[var(--text-muted)]">
                          {report.stockCode}
                        </span>
                        <span
                          className={cn(
                            "ml-auto inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px]",
                            signal?.style ?? "signal-neutral",
                          )}
                        >
                          <Icon className="size-3" />
                          {report.personalModel
                            ? report.personalModel.entryCandidate
                              ? "买入候选"
                              : "复核/观察"
                            : (signal?.label ?? "未提供")}
                        </span>
                      </div>
                      <p className="mt-3 text-xs leading-5 text-[var(--text-secondary)]">
                        {report.personalModel
                          ? `我的投资模型 v${report.personalModel.version} · ${report.personalModel.level}`
                          : `${strategyLabel(report.strategy)} · ${modeLabel(report.mode)}`}
                      </p>
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-[11px] text-[var(--text-muted)]">
                        <time>{formatDate(report.createdAt)}</time>
                        <span>
                          {report.confidence ? confidenceTitle(report.confidence) : "置信度"}{" "}
                          {report.confidence?.value != null
                            ? confidenceValue(report.confidence)
                            : "未提供"}
                        </span>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
        {(selectedReport || detailError) && (
          <section
            ref={detailRef}
            tabIndex={-1}
            aria-label="报告详情"
            className="glass-card min-w-0 p-5 sm:p-6"
          >
            <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="mb-1 text-xs text-[var(--text-muted)]">
                  报告详情
                </p>
                <h2 className="text-xl font-semibold">
                  {selectedReport?.stockName ?? "报告暂不可用"}
                </h2>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => selectReport(null)}
              >
                <X className="size-4" />
                关闭
              </Button>
            </div>
            {detailError && (
              <div role="alert" className="mb-4 text-sm text-[var(--bearish)]">
                {detailError}
              </div>
            )}
            {selectedReport && (
              <>
                <div className="mb-5 flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={!canExportDocument || isPdfGenerating}
                    onClick={() => void handlePdf()}
                  >
                    {isPdfGenerating ? (
                      <Loader2 className="size-3.5 animate-spin" />
                    ) : (
                      <FileText className="size-3.5" />
                    )}
                    导出 PDF
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => downloadReportJson(selectedReport)}
                  >
                    <Download className="size-3.5" />
                    导出 JSON
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={!canExportDocument}
                    onClick={() => downloadReportMarkdown(selectedReport)}
                  >
                    <FileDown className="size-3.5" />
                    导出 Markdown
                  </Button>
                </div>
                <ReportDetail report={selectedReport} />
              </>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
