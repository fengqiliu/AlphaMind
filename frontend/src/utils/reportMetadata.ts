import type { AnalysisReport } from "@/types";

export function strategyLabel(strategy: AnalysisReport["strategy"]): string {
  switch (strategy?.toLowerCase()) {
    case "conservative":
      return "保守策略";
    case "balanced":
      return "平衡策略";
    case "aggressive":
      return "激进策略";
    default:
      return "策略未记录";
  }
}

export function modeLabel(mode: AnalysisReport["mode"]): string {
  switch (mode?.toLowerCase()) {
    case "pipeline":
      return "流水线模式";
    case "debate":
      return "辩论模式";
    default:
      return "模式未记录";
  }
}
