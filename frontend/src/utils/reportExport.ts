import type { AnalysisReport, DebatePosition } from "@/types";
import { SignalType } from "@/types";

function sanitizeFileName(input: string): string {
  return input
    .replace(/[\\/:*?"<>|]/g, "-")
    .replace(/\s+/g, "_")
    .replace(/-+/g, "-")
    .trim();
}

function signalLabel(signal: SignalType): string {
  if (signal === SignalType.BUY) return "买入";
  if (signal === SignalType.SELL) return "卖出";
  return "持有";
}

function debateLabel(position?: DebatePosition): string {
  if (position === "BULLISH") return "看多";
  if (position === "BEARISH") return "看空";
  if (position === "NEUTRAL") return "中性";
  return "-";
}

function getBaseName(report: AnalysisReport): string {
  const raw = `${report.stockCode}_${report.stockName}_${new Date(report.createdAt).toISOString().slice(0, 19)}`;
  return sanitizeFileName(raw);
}

function triggerDownload(content: string, mimeType: string, fileName: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export function reportToMarkdown(report: AnalysisReport): string {
  const confidencePercent = `${(report.confidence.value * 100).toFixed(0)}%`;
  const createdAt = new Date(report.createdAt).toLocaleString("zh-CN");
  const lines = [
    `# ${report.stockName}（${report.stockCode}）分析报告`,
    "",
    `- 生成时间：${createdAt}`,
    `- 最终信号：${signalLabel(report.finalSignal)}`,
    `- 置信度：${confidencePercent}（${report.confidence.level}）`,
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
    "",
    "## 技术面",
    "",
    `- 技术评分：${report.technicalIndicators.technicalScore.toFixed(2)}`,
    `- MACD(DIF/DEA/Hist)：${report.technicalIndicators.macd.dif.toFixed(2)} / ${report.technicalIndicators.macd.dea.toFixed(2)} / ${report.technicalIndicators.macd.histogram.toFixed(2)}`,
    `- RSI(6/12/24)：${report.technicalIndicators.rsi.rsi6.toFixed(2)} / ${report.technicalIndicators.rsi.rsi12.toFixed(2)} / ${report.technicalIndicators.rsi.rsi24.toFixed(2)}`,
    `- KDJ(K/D/J)：${report.technicalIndicators.kdj.k.toFixed(2)} / ${report.technicalIndicators.kdj.d.toFixed(2)} / ${report.technicalIndicators.kdj.j.toFixed(2)}`,
    "",
    "## 舆情面",
    "",
    `- 舆情评分：${(report.sentimentData.sentimentScore * 100).toFixed(0)} / 100`,
    `- 趋势判断：${report.sentimentData.sentimentTrend}`,
    `- 摘要：${report.sentimentData.analysisSummary}`,
    "",
    "### 利好因素",
    ...report.sentimentData.positiveFactors.map((item) => `- ${item}`),
    "",
    "### 风险因素",
    ...report.sentimentData.negativeFactors.map((item) => `- ${item}`),
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
      lines.push("", "### 风险提示", ...report.judgment.riskWarnings.map((r) => `- ${r}`));
    }
  }

  return lines.join("\n");
}

export function downloadReportJson(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.json`;
  triggerDownload(JSON.stringify(report, null, 2), "application/json", fileName);
}

export function downloadReportMarkdown(report: AnalysisReport) {
  const fileName = `${getBaseName(report)}.md`;
  triggerDownload(reportToMarkdown(report), "text/markdown;charset=utf-8", fileName);
}
