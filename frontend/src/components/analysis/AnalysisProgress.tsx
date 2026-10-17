import { Check, Circle, Loader2, Workflow } from "lucide-react";
import { AnalysisMode } from "@/types";
import { cn } from "@/utils";

const STAGES = [
  { key: "MARKET", label: "行情采集", detail: "报价与历史 K 线" },
  { key: "TECHNICAL", label: "技术分析", detail: "趋势、动量与波动" },
  { key: "SENTIMENT", label: "舆情研究", detail: "行情信号与个股新闻" },
  { key: "PORTFOLIO", label: "综合决策", detail: "策略、置信度与风险" },
];

export function AnalysisProgress({
  mode,
  stage,
  isAnalyzing,
  hasReport,
  message,
}: {
  mode: AnalysisMode;
  stage: string;
  isAnalyzing: boolean;
  hasReport: boolean;
  message: string;
}) {
  const stages =
    mode === AnalysisMode.DEBATE
      ? [
          ...STAGES,
          { key: "DEBATE", label: "多空辩论", detail: "三方观点与最终仲裁" },
        ]
      : STAGES;
  const current = stages.findIndex((item) => item.key === stage);
  return (
    <section aria-label="研究流程" className="glass-card h-full p-5 sm:p-6">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-sm font-semibold">研究流程</h2>
        <Workflow className="size-4 text-[var(--text-muted)]" />
      </div>
      <p className="text-xs text-[var(--text-muted)]">
        每一步，构建一个更完整的判断。
      </p>
      <ol className="mt-7 space-y-0">
        {stages.map((item, index) => {
          const done = hasReport || (isAnalyzing && current > index);
          const active = isAnalyzing && current === index;
          return (
            <li
              key={item.key}
              aria-current={active ? "step" : undefined}
              className="relative flex gap-3 pb-6 last:pb-0"
            >
              {index < stages.length - 1 && (
                <span
                  aria-hidden="true"
                  className="absolute top-8 bottom-0 left-[15px] w-px bg-[var(--border)]"
                />
              )}
              <span
                className={cn(
                  "relative flex size-8 shrink-0 items-center justify-center rounded-full border text-xs font-mono",
                  done || active
                    ? "border-transparent bg-[var(--accent-subtle)] text-[var(--accent)]"
                    : "border-[var(--border)] text-[var(--text-muted)]",
                )}
              >
                {done ? (
                  <Check className="size-4" />
                ) : active ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  String(index + 1).padStart(2, "0")
                )}
              </span>
              <div className="min-w-0 pt-0.5">
                <p
                  className={cn(
                    "text-sm font-medium",
                    active && "text-[var(--accent)]",
                  )}
                >
                  {item.label}
                </p>
                <p className="mt-1 text-xs text-[var(--text-muted)]">
                  {item.detail}
                </p>
              </div>
              {done && (
                <span className="ml-auto pt-1 text-[10px] text-[var(--accent)]">
                  完成
                </span>
              )}
            </li>
          );
        })}
      </ol>
      <div
        role="status"
        className="mt-6 flex items-start gap-2 border-t border-[var(--border)] pt-4 text-xs leading-5 text-[var(--text-secondary)]"
      >
        {isAnalyzing ? (
          <Loader2 className="mt-0.5 size-3.5 shrink-0 animate-spin text-[var(--accent)]" />
        ) : (
          <Circle className="mt-1 size-2.5 shrink-0 text-[var(--text-muted)]" />
        )}
        {isAnalyzing
          ? message
          : hasReport
            ? "完整报告已生成，可查看或导出。"
            : "准备就绪，选择标的后开始分析。"}
      </div>
    </section>
  );
}
