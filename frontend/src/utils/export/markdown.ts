/** JSON 与 Markdown 报告导出。 */
import { signalLabel, debateLabel, getBaseName, triggerDownload } from "./shared";
import { confidenceTitle, confidenceValue } from "@/utils/confidenceMetadata";
import { modeLabel, strategyLabel } from "@/utils/reportMetadata";
import { personalModelMarkdown } from "@/utils/personalModelExport";
import type { AnalysisReport } from "@/types";

export function reportToMarkdown(report: AnalysisReport): string {
  if (report.personalModel) return personalModelMarkdown(report);
  const confidencePercent = confidenceValue(report.confidence);
  const createdAt = new Date(report.createdAt).toLocaleString("zh-CN");
  const lines = [
    `# ${report.stockName}（${report.stockCode}）分析报告`,
    "",
    `- 生成时间：${createdAt}`,
    `- 投资策略：${strategyLabel(report.strategy)}`,
    `- 分析模式：${modeLabel(report.mode)}`,
    `- 最终信号：${signalLabel(report.finalSignal)}`,
    `- ${confidenceTitle(report.confidence)}：${confidencePercent}（${report.confidence.level}）`,
    `- 一致度方法：${report.confidence.method || "旧报告未记录"}`,
    `- 一致度说明：${report.confidence.explanation || "未记录"}`,
    "- 此数值不是胜率或收益概率。",
    "",
    "## 交易建议",
    "",
    `- 入场价：¥${report.tradeSignal.entryPrice.toFixed(2)}`,
    `- 目标价：¥${report.tradeSignal.targetPrice.toFixed(2)}`,
    `- 止损价：¥${report.tradeSignal.stopLoss.toFixed(2)}`,
    `- 持仓周期：${report.tradeSignal.holdingPeriodDays} 个交易日`,
    `- 建议理由：${report.tradeSignal.rationale}`,
    "",
    "## 市场数据",
    "",
    `- 当前价：¥${report.marketData.currentPrice.toFixed(2)}`,
    `- 涨跌幅：${report.marketData.changePercent.toFixed(2)}%`,
    `- 成交量：${report.marketData.volume.toFixed(2)}`,
    `- 换手率：${report.marketData.turnoverRate.toFixed(2)}%`,
    `- 数据来源：${report.marketData.dataSource || "未标明"}`,
    `- 数据状态：${report.marketData.dataStatus || "未标明"}`,
    "",
    "## 技术面",
    "",
    `- 技术评分：${report.technicalIndicators.technicalScore.toFixed(2)}`,
    `- 技术口径：${report.technicalIndicators.scoreExplanation || "旧报告未记录"}`,
    ...Object.entries(report.technicalIndicators.scoreContributions ?? {}).map(([name, value]) => `- 技术分项 ${name}：${value}`),
    `- MACD(DIF/DEA/Hist)：${report.technicalIndicators.macd.dif.toFixed(2)} / ${report.technicalIndicators.macd.dea.toFixed(2)} / ${report.technicalIndicators.macd.histogram.toFixed(2)}`,
    `- RSI(6/12/24)：${report.technicalIndicators.rsi.rsi6.toFixed(2)} / ${report.technicalIndicators.rsi.rsi12.toFixed(2)} / ${report.technicalIndicators.rsi.rsi24.toFixed(2)}`,
    `- KDJ(K/D/J)：${report.technicalIndicators.kdj.k.toFixed(2)} / ${report.technicalIndicators.kdj.d.toFixed(2)} / ${report.technicalIndicators.kdj.j.toFixed(2)}`,
    "",
    "## 舆情面",
    "",
    `- 舆情评分：${(report.sentimentData.sentimentScore * 100).toFixed(0)} / 100`,
    `- 趋势判断：${report.sentimentData.sentimentTrend}`,
    `- 评分依据：${report.sentimentData.scoreExplanation || "旧报告未记录评分口径，不能按当前规则重新解释。"}`,
    `- 媒体关注度：${report.sentimentData.mediaAttention == null ? "未提供" : `历史估算 ${(report.sentimentData.mediaAttention * 100).toFixed(0)}%（非实测）`}`,
    ...(report.sentimentData.dataGaps ?? []).map((gap) => `- 数据缺口：${gap}`),
    `- 摘要：${report.sentimentData.analysisSummary}`,
    "",
    "### 利好因素",
    ...report.sentimentData.positiveFactors.map((item) => `- ${item}`),
    "",
    "### 风险因素",
    ...report.sentimentData.negativeFactors.map((item) => `- ${item}`),
    ...(report.sentimentData.recentHeadlines?.length
      ? [
          "",
          `### 最新资讯（来源：${report.sentimentData.newsDataSource || "未标明"}${
            report.sentimentData.newsStale ? "，过期缓存" : ""
          }）`,
          ...report.sentimentData.recentHeadlines.map((item) => `- ${item}`),
        ]
      : []),
  ];

  if (report.judgment) {
    lines.push(
      "",
      "## 辩论裁决",
      "",
      `- 最终立场：${debateLabel(report.judgment.finalPosition)}`,
      `- 裁决说明：${report.judgment.reasoning}`,
    );

    if (report.judgment.riskWarnings?.length) {
      lines.push(
        "",
        "### 风险提示",
        ...report.judgment.riskWarnings.map((r) => `- ${r}`),
      );
    }
  }

  if (report.debateViews?.length) {
    lines.push("", "## 三方辩论观点");
    report.debateViews.forEach((view) => {
      lines.push(
        "",
        `### ${debateLabel(view.position)}`,
        "",
        view.view,
        ...(view.reasons?.length
          ? ["", "支撑理由：", ...view.reasons.map((item) => `- ${item}`)]
          : []),
        ...(view.keyPoints?.length
          ? ["", "核心观察：", ...view.keyPoints.map((item) => `- ${item}`)]
          : []),
        ...(view.attackPoints?.length
          ? ["", "反驳观点：", ...view.attackPoints.map((item) => `- ${item}`)]
          : []),
      );
    });
  }

  return lines.join("\n");
}

export function downloadReportJson(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.json`;
  triggerDownload(
    JSON.stringify(report, null, 2),
    "application/json",
    fileName,
  );
}

export function downloadReportMarkdown(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.md`;
  triggerDownload(
    reportToMarkdown(report),
    "text/markdown;charset=utf-8",
    fileName,
  );
}
