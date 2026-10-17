"use client";

import { create } from "zustand";
import type {
  AnalysisMode,
  AnalysisReport,
  ConfidenceInterval,
  DebateView,
  Judgment,
  MarketData,
  SentimentData,
  SSEEvent,
  TechnicalIndicators,
  TradeSignal,
} from "@/types";

interface AnalysisState {
  currentStockCode: string | null;
  currentStockName: string | null;
  activeMode: AnalysisMode | null;
  reportStrategy: AnalysisReport["strategy"];
  reportMode: AnalysisReport["mode"];
  reportFinalSignal: AnalysisReport["finalSignal"] | null;
  reportId: string | null;
  reportCreatedAt: string | null;
  isAnalyzing: boolean;
  currentStage: string;
  loadingMessage: string;
  marketData: MarketData | null;
  technicalIndicators: TechnicalIndicators | null;
  sentimentData: SentimentData | null;
  judgment: Judgment | null;
  confidence: ConfidenceInterval | null;
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
  setConfidence: (data: ConfidenceInterval) => void;
  setFinalSignal: (signal: TradeSignal) => void;
  setReportMetadata: (id: string, createdAt: string) => void;
  setDebateViews: (views: DebateView[] | null) => void;
  setError: (error: string | null) => void;
  beginAnalysis: (mode: AnalysisMode) => void;
  applyReport: (report: AnalysisReport) => void;
  handleSSEEvent: (event: SSEEvent) => void;
  clearResults: () => void;
  reset: () => void;
}

const EMPTY_RESULTS = {
  activeMode: null,
  reportStrategy: null,
  reportMode: null,
  reportFinalSignal: null,
  reportId: null,
  reportCreatedAt: null,
  isAnalyzing: false,
  currentStage: "",
  loadingMessage: "",
  marketData: null,
  technicalIndicators: null,
  sentimentData: null,
  judgment: null,
  confidence: null,
  finalSignal: null,
  debateViews: null,
  error: null,
};

export const useAnalysisStore = create<AnalysisState>((set) => ({
  ...EMPTY_RESULTS,
  currentStockCode: null,
  currentStockName: null,

  setCurrentStock: (code, name) =>
    set({ ...EMPTY_RESULTS, currentStockCode: code, currentStockName: name }),
  setIsAnalyzing: (isAnalyzing) => set({ isAnalyzing }),
  setCurrentStage: (currentStage, loadingMessage) =>
    set({ currentStage, loadingMessage }),
  setMarketData: (marketData) => set({ marketData }),
  setTechnicalIndicators: (technicalIndicators) => set({ technicalIndicators }),
  setSentimentData: (sentimentData) => set({ sentimentData }),
  setJudgment: (judgment) => set({ judgment }),
  setConfidence: (confidence) => set({ confidence }),
  setFinalSignal: (finalSignal) => set({ finalSignal }),
  setReportMetadata: (reportId, reportCreatedAt) =>
    set({ reportId, reportCreatedAt }),
  setDebateViews: (debateViews) => set({ debateViews }),
  setError: (error) => set({ error, isAnalyzing: false }),

  beginAnalysis: (mode) =>
    set({
      ...EMPTY_RESULTS,
      activeMode: mode,
      isAnalyzing: true,
      currentStage: "START",
      loadingMessage: "正在连接分析服务...",
    }),

  applyReport: (report) =>
    set({
      reportId: report.id,
      reportStrategy: report.strategy ?? null,
      reportMode: report.mode ?? null,
      reportFinalSignal: report.finalSignal ?? report.tradeSignal?.type ?? null,
      reportCreatedAt: report.createdAt,
      marketData: report.marketData ?? null,
      technicalIndicators: report.technicalIndicators ?? null,
      sentimentData: report.sentimentData ?? null,
      finalSignal: report.tradeSignal ?? null,
      confidence: report.confidence ?? report.judgment?.confidence ?? null,
      judgment: report.judgment ?? null,
      debateViews: report.debateViews ?? null,
      currentStage: "COMPLETE",
      loadingMessage: "分析完成",
      isAnalyzing: false,
      error: null,
    }),

  handleSSEEvent: (event) => {
    switch (event.event) {
      case "stage":
        set({
          currentStage: event.stage || "",
          loadingMessage: event.message || "",
        });
        break;
      case "data": {
        const agent = event.agentType || event.stage;
        if (agent === "MARKET") set({ marketData: event.data as MarketData });
        else if (agent === "TECHNICAL")
          set({ technicalIndicators: event.data as TechnicalIndicators });
        else if (agent === "SENTIMENT")
          set({ sentimentData: event.data as SentimentData });
        else if (agent === "PORTFOLIO")
          set({ finalSignal: event.data as TradeSignal });
        else if (agent === "ARBITRATOR" || agent === "DEBATE")
          set({ judgment: event.data as Judgment });
        else if (agent === "BULL" || agent === "BEAR" || agent === "NEUTRAL") {
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
        set({ loadingMessage: "分析阶段已完成，正在接收完整报告..." });
        break;
      case "error":
        set({ error: event.message || "分析失败", isAnalyzing: false });
        break;
    }
  },
  clearResults: () => set(EMPTY_RESULTS),
  reset: () =>
    set({ ...EMPTY_RESULTS, currentStockCode: null, currentStockName: null }),
}));
