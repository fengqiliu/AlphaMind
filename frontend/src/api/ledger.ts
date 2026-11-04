import api from "@/api/client";
import type { PerformanceSummary, SignalOutcome } from "@/types/ledger";

export const getSignalPerformance = async (): Promise<PerformanceSummary> => {
  const { data } = await api.get("/signals/performance");
  if (data.code !== 200 || !data.data) throw new Error(data.message || "信号表现加载失败");
  return data.data;
};

export const getReportOutcomes = async (reportId: string): Promise<SignalOutcome[]> => {
  const { data } = await api.get(`/signals/outcomes/${encodeURIComponent(reportId)}`);
  if (data.code !== 200) throw new Error(data.message || "事后表现加载失败");
  return data.data ?? [];
};
