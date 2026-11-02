/** 导出共用的转义、文件名、标签与下载辅助函数。 */
import type { AnalysisReport, DebatePosition } from "@/types";
import { SignalType } from "@/types";

export function escapeHtml(input: string): string {
  return input.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[character]!);
}

export function sanitizeFileName(input: string): string {
  return input
    .replace(/[\\/:*?"<>|]/g, "-")
    .replace(/\s+/g, "_")
    .replace(/-+/g, "-")
    .trim();
}

export function signalLabel(signal: SignalType): string {
  if (signal === SignalType.BUY) return "买入";
  if (signal === SignalType.SELL) return "卖出";
  return "持有";
}

export function debateLabel(position?: DebatePosition): string {
  if (position === "BULLISH") return "看多";
  if (position === "BEARISH") return "看空";
  if (position === "NEUTRAL") return "中性";
  return "-";
}

export function getBaseName(report: AnalysisReport): string {
  const raw = `${report.stockCode}_${report.stockName}_${new Date(report.createdAt).toISOString().slice(0, 19)}`;
  return sanitizeFileName(raw);
}

export function triggerDownload(content: string, mimeType: string, fileName: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
