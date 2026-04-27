import axios from "axios";
import type {
  StockSearchResult,
  WatchlistItem,
  AnalysisReport,
  ChatMessage,
  SSEEvent,
} from "@/types";

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || "/api/v1",
  timeout: 60000,
});

export const searchStocks = async (
  keyword: string,
): Promise<StockSearchResult[]> => {
  const { data } = await api.get(
    `/stocks/search?keyword=${encodeURIComponent(keyword)}`,
  );
  return data.data;
};

export const getWatchlist = async (): Promise<WatchlistItem[]> => {
  const { data } = await api.get("/watchlist");
  return data.data;
};

export const addToWatchlist = async (stockCode: string): Promise<void> => {
  await api.post("/watchlist", { stockCode });
};

export const removeFromWatchlist = async (stockCode: string): Promise<void> => {
  await api.delete(`/watchlist/${stockCode}`);
};

export const getAnalysisReport = async (
  reportId: string,
): Promise<AnalysisReport> => {
  const { data } = await api.get(`/analysis/${reportId}`);
  return data.data;
};

export const getAnalysisHistory = async (
  stockCode?: string,
  limit = 20,
): Promise<AnalysisReport[]> => {
  const params = new URLSearchParams();
  if (stockCode) params.append("stockCode", stockCode);
  params.append("limit", limit.toString());
  const { data } = await api.get(`/analysis/history?${params}`);
  return data.data;
};

export const createChatSession = async (
  stockCode: string,
  strategy?: string,
): Promise<string> => {
  const { data } = await api.post("/chat/session", { stockCode, strategy });
  return data.data.sessionId;
};

export const getChatMessages = async (
  sessionId: string,
): Promise<ChatMessage[]> => {
  const { data } = await api.get(`/chat/session/${sessionId}/messages`);
  return data.data;
};

export const exportReport = async (
  reportId: string,
  format: "json" | "pdf" | "markdown",
): Promise<Blob> => {
  const { data } = await api.get(`/analysis/export/${reportId}`, {
    params: { format },
    responseType: "blob",
  });
  return data;
};

export default api;
