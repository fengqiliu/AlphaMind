import type { AnalysisReport } from "@/types";
import { AnalysisResult } from "@/components/analysis/AnalysisResult";
import { DebateResult } from "@/components/analysis/DebateResult";
import { MarketOverview } from "@/components/analysis/MarketOverview";
import { SentimentPanel } from "@/components/analysis/SentimentPanel";
import { TechnicalIndicatorsPanel } from "@/components/chart/TechnicalIndicators";
import { modeLabel, strategyLabel } from "@/utils/reportMetadata";
import { formatDate } from "@/utils";
import { PersonalModelPanel } from "@/components/analysis/PersonalModelPanel";

/** 首页与历史详情共用分析展示模块，历史页不重新计算已保存的结论。 */
export function ReportDetail({ report }: { report: AnalysisReport }) {
  if (report.personalModel)
    return (
      <div className="space-y-5">
        <PersonalModelPanel
          assessment={report.personalModel}
          marketData={report.marketData}
        />
        {report.marketData && <MarketOverview marketData={report.marketData} />}
      </div>
    );
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-2 text-xs text-[var(--text-secondary)]">
        <span className="rounded-lg bg-[var(--accent-subtle)] px-3 py-2 text-[var(--accent)]">
          {strategyLabel(report.strategy)}
        </span>
        <span className="rounded-lg bg-[var(--bg-tertiary)] px-3 py-2">
          {modeLabel(report.mode)}
        </span>
        <span className="px-1 py-2">{formatDate(report.createdAt)}</span>
      </div>
      {report.tradeSignal ? (
        <AnalysisResult
          signal={report.tradeSignal}
          confidence={report.confidence ?? undefined}
        />
      ) : (
        <p className="text-sm text-[var(--text-muted)]">此记录未保存交易建议</p>
      )}
      {report.judgment && (
        <section
          aria-label="历史辩论裁决"
          className="border-t border-[var(--border)] pt-5"
        >
          <h3 className="mb-4 text-base font-semibold">辩论裁决</h3>
          {!report.debateViews?.length && (
            <p className="mb-4 text-xs text-[var(--text-muted)]">
              此历史记录未保存三方观点，以下为已保存的仲裁结果。
            </p>
          )}
          <DebateResult
            judgment={report.judgment}
            debateViews={report.debateViews}
          />
        </section>
      )}
      {report.marketData && <MarketOverview marketData={report.marketData} />}
      {report.technicalIndicators && (
        <section className="glass-card p-4">
          <h3 className="mb-4 text-base font-semibold">技术指标</h3>
          <TechnicalIndicatorsPanel data={report.technicalIndicators} />
        </section>
      )}
      {report.sentimentData && (
        <SentimentPanel sentimentData={report.sentimentData} />
      )}
    </div>
  );
}
