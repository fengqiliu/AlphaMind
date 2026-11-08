/** 一条持仓：有代码和股数时按实时价估值，否则用手工市值（如场外基金）。 */
export interface Holding {
  stockCode: string | null;
  name: string;
  theme: string;
  shares: number | null;
  manualValue: number | null;
}

/** 手动维护的当前组合，金额单位为元。 */
export interface PortfolioProfile {
  holdings: Holding[];
  cash: number;
  lowVolatility: number;
  peakValue: number | null;
  updatedAt?: string | null;
}

export interface HoldingValuation {
  stockCode: string | null;
  name: string;
  theme: string;
  shares: number | null;
  price: number | null;
  marketValue: number;
  source: string;
}

/** 按组合推算的 Gate 5 数值；complete 为 false 时缺失项为 null，原因见 problems。 */
export interface RiskBudget {
  stockCode: string;
  theme: string;
  plannedBuyAmount: number;
  complete: boolean;
  totalValue: number | null;
  projectedPositionPct: number | null;
  projectedThemePct: number | null;
  cashAfterTradePct: number | null;
  portfolioDrawdownPct: number | null;
  peakValue: number | null;
  valuations: HoldingValuation[];
  problems: string[];
  computedAt: string;
}

export type HealthKind =
  | "POSITION_LIMIT"
  | "THEME_LIMIT"
  | "CASH_FLOOR"
  | "DRAWDOWN"
  | "OVERHEATED"
  | "REVIEW_MISSING"
  | "REVIEW_EXPIRED";

export interface HealthFinding {
  kind: HealthKind;
  stockCode: string | null;
  name: string;
  message: string;
  value: number | null;
  limit: number | null;
}

/** 持仓体检；totalValue 为 null 表示有持仓缺当期行情，组合权重未检查。 */
export interface PortfolioHealth {
  totalValue: number | null;
  findings: HealthFinding[];
  unavailable: string[];
  checkedAt: string;
}

/** 某个交易日收盘后的组合总资产；drawdownPct 相对此前最高快照。 */
export interface PortfolioSnapshotPoint {
  date: string;
  totalValue: number;
  cashRatioPct: number;
  holdingsCount: number;
  drawdownPct: number;
}
