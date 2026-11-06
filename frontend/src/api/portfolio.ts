import api from "@/api/client";
import type { PortfolioProfile, RiskBudget } from "@/types/portfolio";

/** 尚未录入时返回 null。 */
export const getPortfolio = async (): Promise<PortfolioProfile | null> => {
  const { data } = await api.get("/portfolio");
  if (data.code === 404) return null;
  if (data.code !== 200 || !data.data) throw new Error(data.message || "持仓加载失败");
  return data.data;
};

export const savePortfolio = async (profile: PortfolioProfile): Promise<PortfolioProfile> => {
  const { data } = await api.put("/portfolio", profile);
  if (data.code !== 200 || !data.data) throw new Error(data.message || "持仓保存失败");
  return data.data;
};

export const computeRiskBudget = async (
  stockCode: string,
  plannedBuyAmount: number,
): Promise<RiskBudget> => {
  const { data } = await api.post(
    "/portfolio/risk-budget",
    { stockCode, plannedBuyAmount },
    { timeout: 120000 },
  );
  if (data.code !== 200 || !data.data) throw new Error(data.message || "组合推算失败");
  return data.data;
};
