import api from "@/api/client";
import type { PriceAlert, PriceAlertCheckSummary } from "@/types/alerts";

export const getPriceAlerts = async (): Promise<PriceAlert[]> => {
  const { data } = await api.get("/investment-model/alerts");
  if (data.code !== 200) throw new Error(data.message || "价格提醒加载失败");
  return data.data ?? [];
};

export const acknowledgePriceAlert = async (id: number): Promise<void> => {
  const { data } = await api.post(`/investment-model/alerts/${id}/ack`);
  if (data.code !== 200) throw new Error(data.message || "标记已读失败");
};

export const checkPriceAlerts = async (): Promise<PriceAlertCheckSummary> => {
  const { data } = await api.post("/investment-model/alerts/check", null, { timeout: 120000 });
  if (data.code !== 200 || !data.data) throw new Error(data.message || "档位检查失败");
  return data.data;
};
