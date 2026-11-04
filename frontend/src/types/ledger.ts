/** 信号账本：一份报告在某个持有期的事后结果（到期后写入，不再修改）。 */
export interface SignalOutcome {
  reportId: string;
  stockCode: string;
  horizonDays: number;
  signalType: string | null;
  confidenceValue: number | null;
  signalDate: string;
  entryDate: string;
  entryPrice: number;
  exitDate: string;
  exitPrice: number;
  grossReturn: number;
  netReturn: number;
  benchmarkReturn: number | null;
  excessReturn: number | null;
  entryBlocked: boolean;
}

export type PerformanceDimension =
  | "signal"
  | "buyConfidence"
  | "strategy"
  | "mode"
  | "personalLevel";

export interface PerformanceRow {
  dimension: PerformanceDimension;
  key: string;
  horizonDays: number;
  count: number;
  meanNetReturn: number | null;
  meanExcessReturn: number | null;
  hitRate: number | null;
  directionalCount: number;
  insufficient: boolean;
}

export interface PerformanceSummary {
  evaluatedCount: number;
  blockedCount: number;
  rows: PerformanceRow[];
}
