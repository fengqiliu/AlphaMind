import type { SentimentData } from "@/types";
import { Newspaper, ThumbsUp, ThumbsDown } from "lucide-react";

export function SentimentPanel({
  sentimentData,
}: {
  sentimentData: SentimentData;
}) {
  return (
    <div className="glass-card p-5 animate-enter delay-250">
      <h2 className="mb-4 flex flex-wrap items-center gap-2 text-lg font-semibold">
        <span className="w-1.5 h-5 bg-[var(--neutral)] rounded-full" />
        舆情分析
        {sentimentData.newsDataSource && (
          <span
            className={
              "text-xs font-mono font-normal ml-1 " +
              (sentimentData.newsStale
                ? "text-[var(--neutral)]"
                : "text-[var(--bullish)]")
            }
          >
            {sentimentData.newsStale ? "过期缓存" : "实时资讯"}
            {` · ${sentimentData.newsDataSource}`}
          </span>
        )}
      </h2>
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-4 p-3 bg-[var(--bg-tertiary)] rounded-xl">
          <div className="flex items-center gap-2">
            <Newspaper className="w-4 h-4 text-[var(--text-muted)]" />
            <span className="text-xs font-mono text-[var(--text-muted)]">
              综合评分
            </span>
          </div>
          <div className="text-2xl font-bold font-mono">
            {Math.round(sentimentData.sentimentScore * 100)}
            <span className="text-sm font-normal text-[var(--text-muted)]">
              /100
            </span>
          </div>
          <span className="ml-auto text-sm font-mono text-[var(--text-secondary)]">
            {sentimentData.sentimentTrend}
          </span>
          <span className="text-xs font-mono text-[var(--text-muted)]">
            {sentimentData.newsSignal != null &&
              `新闻信号 ${Math.round(sentimentData.newsSignal * 100)} · `}
            {sentimentData.mediaAttention == null
              ? "媒体关注度未提供"
              : `历史关注度估算 ${Math.round(sentimentData.mediaAttention * 100)}%（非实测）`}
          </span>
        </div>

        <details className="rounded-xl border border-[var(--border)] p-3" open>
          <summary className="cursor-pointer text-sm font-medium focus-visible:outline-2 focus-visible:outline-[var(--accent)]">
            评分依据与数据缺口
          </summary>
          <p className="mt-2 text-sm leading-relaxed text-[var(--text-secondary)]">
            {sentimentData.scoreExplanation || "旧报告未记录评分口径，不能按当前规则重新解释。"}
          </p>
          {sentimentData.dataGaps && sentimentData.dataGaps.length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-xs leading-relaxed text-[var(--text-muted)]">
              {sentimentData.dataGaps.map((gap, i) => <li key={i}>{gap}</li>)}
            </ul>
          )}
        </details>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {sentimentData.positiveFactors?.length > 0 && (
            <div>
              <div className="text-xs font-mono text-[var(--bullish)] mb-2 flex items-center gap-1">
                <ThumbsUp className="w-3 h-3" />
                利好因素
              </div>
              <ul className="space-y-1">
                {sentimentData.positiveFactors.map((f, i) => (
                  <li
                    key={i}
                    className="text-sm text-[var(--text-secondary)] flex gap-2"
                  >
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
                  <li
                    key={i}
                    className="text-sm text-[var(--text-secondary)] flex gap-2"
                  >
                    <span className="text-[var(--bearish)] shrink-0">-</span>
                    {f}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {sentimentData.recentHeadlines &&
          sentimentData.recentHeadlines.length > 0 && (
            <div>
              <div className="text-xs font-mono text-[var(--text-muted)] mb-2 flex items-center gap-1">
                <Newspaper className="w-3 h-3" />
                最新资讯
              </div>
              <ul className="space-y-1">
                {sentimentData.recentHeadlines.map((headline, i) => (
                  <li
                    key={i}
                    className="text-sm text-[var(--text-secondary)] flex gap-2 leading-relaxed"
                  >
                    <span className="text-[var(--text-muted)] shrink-0">·</span>
                    {headline}
                  </li>
                ))}
              </ul>
            </div>
          )}

        {sentimentData.analysisSummary && (
          <p className="text-sm text-[var(--text-muted)] font-mono border-t border-[var(--border)] pt-3 leading-relaxed">
            {sentimentData.analysisSummary}
          </p>
        )}
      </div>
    </div>
  );
}
