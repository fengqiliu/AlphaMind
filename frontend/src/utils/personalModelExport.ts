import type { AnalysisReport } from "@/types";

export function personalModelMarkdown(report: AnalysisReport): string {
  const a = report.personalModel!;
  return [
    `# ${a.asset.name}（${a.asset.code}）个人投资模型复核`,
    "",
    `- 模型：v${a.version} / ${a.versionDate}`,
    `- 复核时间：${report.createdAt}`,
    `- 信号等级：${a.level}`,
    `- 动作：${a.action}`,
    `- 最新价格：${a.currentPrice ?? "未提供"} / ${a.priceZone}`,
    "",
    "## 五道决策门与风险预算",
    "",
    ...a.gates.map((g) => `- ${g.label}：${g.status}。${g.detail}`),
    "",
    "## 价格三档",
    "",
    ...a.asset.bands.map(
      (b) =>
        `- 第${b.tier}档：${b.low}–${b.high}${a.asset.provisional ? "（暂定）" : ""}`,
    ),
    "",
    "## 择时数据",
    "",
    `- 日 RSI14：${a.timing.dailyRsi14 ?? "未提供"}`,
    `- 已结束周 RSI14：${a.timing.weeklyRsi14 ?? "未提供"}`,
    `- ATR20：${a.timing.atr20 ?? "未提供"}`,
    `- ATR/价格：${a.timing.atrPricePct ?? "未提供"}%`,
    `- 下一档复核价格上界：${a.timing.nextBatchPriceCeiling ?? "未提供"}`,
    "",
    "## 待补证据",
    "",
    ...a.missingEvidence.map((s) => `- ${s}`),
    "",
    "## 未通过约束",
    "",
    ...a.blockers.map((s) => `- ${s}`),
    "",
    "## 财报与估值自动取数",
    "",
    ...(a.fundamentals
      ? [
          `- 来源：${a.fundamentals.source}（${a.fundamentals.status}，取数日 ${a.fundamentals.retrievedOn}）`,
          ...(a.fundamentals.warning ? [`- 警告：${a.fundamentals.warning}`] : []),
          `- 摘要：${a.fundamentals.evidenceSummary}`,
          ...a.fundamentals.observations.map((s) => `- ${s}`),
          `- ${a.fundamentals.disclaimer}`,
        ]
      : ["- 未取得（数据源不可用、该证券无财报，或为接入前的旧报告）"]),
    "",
    "## 手工证据快照",
    "",
    ...(a.evidence.sourceReportId
      ? [`- 复核来源报告：${a.evidence.sourceReportId}`]
      : []),
    `- 复核日期：${a.evidence.reviewedAt ?? "未提供"}`,
    `- 有效至：${a.evidence.validUntil ?? "未提供"}`,
    `- 来源与报告期：${a.evidence.evidenceSource || "未提供"}`,
    `- 反方理由：${a.evidence.counterArgument || "未提供"}`,
    "",
    "```json",
    JSON.stringify(a.evidence, null, 2),
    "```",
    "",
    "## 规则边界",
    "",
    ...a.notes.map((s) => `- ${s}`),
    "",
    `原文 SHA256：${a.sourceSha256}`,
  ].join("\n");
}
