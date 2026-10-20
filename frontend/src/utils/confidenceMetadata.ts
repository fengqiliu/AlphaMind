import type { ConfidenceInterval } from "@/types";

export function isRuleAgreement(confidence?: ConfidenceInterval | null) {
  return confidence?.method === "RULE_AGREEMENT_V1";
}

export function confidenceTitle(confidence?: ConfidenceInterval | null) {
  return isRuleAgreement(confidence) ? "规则一致度" : "历史置信度";
}

export function confidenceValue(confidence: ConfidenceInterval) {
  return `${Math.round(confidence.value * 100)}${isRuleAgreement(confidence) ? "/100" : "%"}`;
}
