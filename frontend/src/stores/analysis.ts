"use client";

import { create } from "zustand";
import type {
  SSEEvent,
  TradeSignal,
  MarketData,
  TechnicalIndicators,
  SentimentData,
  Judgment,
  DebateView,
} from "@/types";

interface AnalysisState {
  currentStockCode: string | null;
  currentStockName: string | null;
  isAnalyzing: boolean;
  currentStage: string;
  loadingMessage: string;
  marketData: MarketData | null;
  technicalIndicators: TechnicalIndicators | null;
  sentimentData: SentimentData | null;
  judgment: Judgment | null;
  finalSignal: TradeSignal | null;
  debateViews: DebateView[] | null;
  error: string | null;

  setCurrentStock: (code: string, name: string) => void;
  setIsAnalyzing: (analyzing: boolean) => void;
  setCurrentStage: (stage: string, message: string) => void;
  setMarketData: (data: MarketData) => void;
  setTechnicalIndicators: (data: TechnicalIndicators) => void;
  setSentimentData: (data: SentimentData) => void;
  setJudgment: (data: Judgment) => void;
  setFinalSignal: (signal: TradeSignal) => void;
  setDebateViews: (views: DebateView[] | null) => void;
  setError: (error: string | null) => void;
  handleSSEEvent: (event: SSEEvent) => void;
  reset: () => void;
}

export const useAnalysisStore = create<AnalysisState>((set) => ({
  currentStockCode: null,
  currentStockName: null,
  isAnalyzing: false,
  currentStage: "",
  loadingMessage: "",
  marketData: null,
  technicalIndicators: null,
  sentimentData: null,
  judgment: null,
  finalSignal: null,
  debateViews: null,
  error: null,

  setCurrentStock: (code, name) =>
    set({ currentStockCode: code, currentStockName: name, error: null }),

  setIsAnalyzing: (analyzing) => set({ isAnalyzing: analyzing }),

  setCurrentStage: (stage, message) =>
    set({ currentStage: stage, loadingMessage: message }),

  setMarketData: (data) => set({ marketData: data }),

  setTechnicalIndicators: (data) => set({ technicalIndicators: data }),

  setSentimentData: (data) => set({ sentimentData: data }),

  setJudgment: (data) => set({ judgment: data }),

  setFinalSignal: (signal) => set({ finalSignal: signal }),

  setDebateViews: (views) => set({ debateViews: views }),

  setError: (error) => set({ error, isAnalyzing: false }),

  handleSSEEvent: (event) => {
    switch (event.event) {
      case "stage":
        set({
          currentStage: event.stage || "",
          loadingMessage: event.message || "",
        });
        break;
      case "data": {
        // 后端 dataEvent 使用 agentType 字段区分数据类型
        const agentType = event.agentType || event.stage;
        if (agentType === "MARKET") set({ marketData: event.data as MarketData });
        else if (agentType === "TECHNICAL")
          set({ technicalIndicators: event.data as TechnicalIndicators });
        else if (agentType === "SENTIMENT")
          set({ sentimentData: event.data as SentimentData });
        else if (agentType === "PORTFOLIO")
          set({ finalSignal: event.data as TradeSignal });
        else if (agentType === "ARBITRATOR" || agentType === "DEBATE")
          set({ judgment: event.data as Judgment });
        else if (agentType === "BULL" || agentType === "BEAR" || agentType === "NEUTRAL") {
          // 累积三方辩论观点
          set((state) => ({
            debateViews: [
              ...(state.debateViews ?? []),
              event.data as DebateView,
            ],
          }));
        }
        break;
      }
      case "complete":
        set({ isAnalyzing: false, currentStage: "COMPLETE" });
        break;
      case "error":
        set({ error: event.message || "分析失败", isAnalyzing: false });
        break;
    }
  },

  reset: () =>
    set({
      currentStockCode: null,
      currentStockName: null,
      isAnalyzing: false,
      currentStage: "",
      loadingMessage: "",
      marketData: null,
      technicalIndicators: null,
      sentimentData: null,
      judgment: null,
      finalSignal: null,
      debateViews: null,
      error: null,
    }),
}));
