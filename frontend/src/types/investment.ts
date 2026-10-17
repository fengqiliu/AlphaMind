export type Decision = "UNKNOWN" | "PASS" | "FAIL";
export type Valuation = "UNKNOWN" | "REASONABLE" | "UNDERVALUED" | "OVERVALUED";
export interface PersonalAsset {
  code: string;
  name: string;
  category: string;
  theme: string;
  bands: { tier: number; low: number; high: number }[];
  valuationFocus: string;
  fundamentalFocus: string;
  vetoRisk: string;
  specialChecks: Record<string, string>;
  provisional: boolean;
}
export interface PersonalModelDefinition {
  id: string;
  name: string;
  version: string;
  versionDate: string;
  sourceFile: string;
  sourceSha256: string;
  principle: string;
  assets: PersonalAsset[];
}
export interface PersonalModelReview {
  sourceReportId?: string;
  reviewedAt?: string;
  validUntil?: string;
  evidenceSource?: string;
  counterArgument?: string;
  valuation?: Valuation;
  earnings?: Decision;
  cashFlow?: Decision;
  businessLogic?: Decision;
  valueTrapClear?: Decision;
  noLeverage?: Decision;
  weeklyTiming?: Decision;
  bottomDivergence?: Decision;
  volatilityContraction?: Decision;
  specialChecks?: Record<string, Decision>;
  projectedPositionPct?: number | null;
  projectedThemePct?: number | null;
  cashAfterTradePct?: number | null;
  portfolioDrawdownPct?: number | null;
  goldPositionLimitPct?: number | null;
}
export interface PersonalReviewDraft {
  sourceReportId: string;
  stockCode: string;
  stockName: string;
  modelVersion: string;
  sourceCreatedAt: string;
  expired: boolean;
  review: PersonalModelReview;
  notices: string[];
}
export interface PersonalModelAssessment {
  modelId: string;
  version: string;
  versionDate: string;
  sourceSha256: string;
  asset: PersonalAsset;
  level: string;
  action: string;
  entryCandidate: boolean;
  priceZone: string;
  currentPrice?: number | null;
  gates: {
    id: string;
    label: string;
    status: "PASS" | "FAIL" | "PENDING";
    detail: string;
  }[];
  timing: {
    dailyRsi14?: number | null;
    weeklyRsi14?: number | null;
    atr20?: number | null;
    atrPricePct?: number | null;
    trueRange?: number | null;
    nextBatchPriceCeiling?: number | null;
    dataAsOf?: string | null;
  };
  missingEvidence: string[];
  blockers: string[];
  notes: string[];
  evidence: PersonalModelReview;
  assessedOn: string;
}
