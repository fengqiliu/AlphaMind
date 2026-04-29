"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/common/Button";
import { ConfidenceBar } from "@/components/common/ConfidenceBar";
import { getAnalysisHistory } from "@/api/client";
import type { AnalysisReport } from "@/types";
import { SignalType as ST } from "@/types";
import {
  History,
  FileText,
  Download,
  FileDown,
  ChevronRight,
  Loader2,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
} from "lucide-react";
import { cn, formatDate } from "@/utils";
import { downloadReportJson, downloadReportMarkdown } from "@/utils/reportExport";

export default function HistoryPage() {
  const [reports, setReports] = useState<AnalysisReport[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedReport, setSelectedReport] = useState<AnalysisReport | null>(
    null,
  );

  useEffect(() => {
    getAnalysisHistory()
      .then(setReports)
      .catch(() => setError("加载历史记录失败"))
      .finally(() => setIsLoading(false));
  }, []);

  const handleDownloadJson = (e: React.MouseEvent, report: AnalysisReport) => {
    e.stopPropagation();
    downloadReportJson(report);
  };

  const handleDownloadMarkdown = (e: React.MouseEvent, report: AnalysisReport) => {
    e.stopPropagation();
    downloadReportMarkdown(report);
  };

  const signalConfig = {
    [ST.BUY]: {
      class: "signal-bullish",
      icon: ArrowUpRight,
      label: "买入",
    },
    [ST.SELL]: {
      class: "signal-bearish",
      icon: ArrowDownRight,
      label: "卖出",
    },
    [ST.HOLD]: {
      class: "signal-neutral",
      icon: Minus,
      label: "持有",
    },
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between animate-enter">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="gradient-text">历史记录</span>
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-1 font-mono">
            ANALYSIS HISTORY & REPORTS
          </p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 glass-card">
          <History className="w-4 h-4 text-[var(--accent)]" />
          <span className="text-xs font-mono text-[var(--text-secondary)]">
            {reports.length} 条记录
          </span>
        </div>
      </div>

      {/* History List */}
      <div className="glass-card overflow-hidden animate-enter delay-100">
        <div className="p-4 border-b border-[var(--border)]">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <History className="w-5 h-5 text-[var(--accent)]" />
            分析历史
          </h2>
        </div>

        {isLoading ? (
          <div className="p-8 flex items-center justify-center">
            <Loader2 className="w-6 h-6 text-[var(--accent)] animate-spin" />
          </div>
        ) : error ? (
          <div className="p-8 text-center">
            <p className="text-[var(--bearish)] text-sm">{error}</p>
          </div>
        ) : reports.length === 0 ? (
          <div className="p-12 text-center">
            <div className="w-16 h-16 mx-auto mb-4 bg-[var(--bg-tertiary)] rounded-2xl flex items-center justify-center">
              <FileText className="w-8 h-8 text-[var(--text-muted)]" />
            </div>
            <p className="text-[var(--text-secondary)] mb-2">暂无分析记录</p>
            <p className="text-sm text-[var(--text-muted)] font-mono">
              开始分析股票后将自动保存历史
            </p>
          </div>
        ) : (
          <div className="divide-y divide-[var(--border)]">
            {reports.map((report) => {
              const config = signalConfig[report.finalSignal];
              const SignalIcon = config.icon;

              return (
                <div
                  key={report.id}
                  className={cn(
                    "p-4 hover:bg-[var(--bg-tertiary)] transition-all cursor-pointer group",
                    selectedReport?.id === report.id &&
                      "bg-[var(--accent-subtle)]",
                  )}
                  onClick={() => setSelectedReport(report)}
                >
                  <div className="flex items-center gap-4">
                    {/* Stock Icon */}
                    <div
                      className={cn(
                        "w-12 h-12 rounded-xl flex items-center justify-center",
                        selectedReport?.id === report.id
                          ? "bg-[var(--accent-glow)]"
                          : "bg-[var(--bg-tertiary)]",
                      )}
                    >
                      <span className="font-mono font-bold text-sm text-[var(--text-primary)]">
                        {report.stockCode.slice(0, 3)}
                      </span>
                    </div>

                    {/* Stock Info */}
                    <div className="flex-1">
                      <div className="flex items-center gap-3">
                        <span className="font-bold text-[var(--text-primary)] text-lg">
                          {report.stockName}
                        </span>
                        <span className="text-sm text-[var(--text-muted)] font-mono">
                          {report.stockCode}
                        </span>
                        <span
                          className={cn(
                            "inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium",
                            config.class,
                          )}
                        >
                          <SignalIcon className="w-3 h-3" />
                          {config.label}
                        </span>
                      </div>
                      <div className="flex items-center gap-4 mt-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-[var(--text-muted)]">
                            置信度
                          </span>
                          <div className="w-24">
                            <ConfidenceBar
                              confidence={report.confidence}
                              showLabel={false}
                              className="h-1.5"
                            />
                          </div>
                          <span className="text-xs font-mono text-[var(--text-secondary)]">
                            {(report.confidence.value * 100).toFixed(0)}%
                          </span>
                        </div>
                        <span className="text-xs text-[var(--text-muted)] font-mono">
                          {formatDate(report.createdAt)}
                        </span>
                      </div>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={(e) => handleDownloadJson(e, report)}
                        className="text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]"
                        title="导出 JSON"
                      >
                        <Download className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={(e) => handleDownloadMarkdown(e, report)}
                        className="text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]"
                        title="导出 Markdown"
                      >
                        <FileDown className="w-4 h-4" />
                      </Button>
                      <ChevronRight className="w-5 h-5 text-[var(--text-muted)]" />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Report Detail */}
      {selectedReport && (
        <div className="glass-card-glow p-5 animate-enter delay-200">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-semibold flex items-center gap-2">
              <FileText className="w-5 h-5 text-[var(--accent)]" />
              报告详情 - {selectedReport.stockName}
            </h3>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setSelectedReport(null)}
              className="border-[var(--border)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]"
            >
              关闭
            </Button>
          </div>
          <div className="space-y-4">
            {/* Price Info */}
            <div className="grid grid-cols-3 gap-4">
              <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl">
                <div className="text-xs text-[var(--text-muted)] font-mono mb-1">
                  入场价
                </div>
                <div className="font-mono font-bold text-xl text-[var(--text-primary)]">
                  ¥{selectedReport.tradeSignal.entryPrice.toFixed(2)}
                </div>
              </div>
              <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl">
                <div className="text-xs text-[var(--text-muted)] font-mono mb-1">
                  目标价
                </div>
                <div className="font-mono font-bold text-xl text-[var(--bullish)]">
                  ¥{selectedReport.tradeSignal.targetPrice.toFixed(2)}
                </div>
              </div>
              <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl">
                <div className="text-xs text-[var(--text-muted)] font-mono mb-1">
                  止损价
                </div>
                <div className="font-mono font-bold text-xl text-[var(--bearish)]">
                  ¥{selectedReport.tradeSignal.stopLoss.toFixed(2)}
                </div>
              </div>
            </div>

            {/* Rationale */}
            <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl">
              <div className="text-xs text-[var(--text-muted)] font-mono mb-2">
                投资理由
              </div>
              <p className="text-sm text-[var(--text-secondary)] leading-relaxed">
                {selectedReport.tradeSignal.rationale}
              </p>
            </div>

            {/* Confidence */}
            <div className="p-4 bg-[var(--bg-tertiary)] rounded-xl">
              <div className="text-xs text-[var(--text-muted)] font-mono mb-2">
                综合置信度
              </div>
              <ConfidenceBar confidence={selectedReport.confidence} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
