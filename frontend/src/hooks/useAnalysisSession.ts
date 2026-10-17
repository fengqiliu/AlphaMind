"use client";

import { useCallback, useEffect } from "react";
import { useSSE } from "@/hooks/useSSE";
import { useAnalysisStore } from "@/stores/analysis";
import type {
  AnalysisMode,
  AnalysisReport,
  SSEEvent,
  StrategyType,
} from "@/types";

/** 管理一次分析的连接、完成、取消与切换标的；页面只负责呈现。 */
export function useAnalysisSession() {
  const { connect, disconnect } = useSSE();

  const selectStock = useCallback(
    (code: string, name: string) => {
      disconnect();
      useAnalysisStore.getState().setCurrentStock(code, name);
    },
    [disconnect],
  );

  const start = useCallback(
    (strategy: StrategyType, mode: AnalysisMode) => {
      const state = useAnalysisStore.getState();
      if (!state.currentStockCode) return;

      disconnect();
      state.beginAnalysis(mode);
      const params = new URLSearchParams({
        stockCode: state.currentStockCode,
        stockName: state.currentStockName || state.currentStockCode,
        strategy,
        mode,
      });

      connect(`/api/v1/analysis/stream?${params}`, {
        onMessage: (event, data) => {
          const store = useAnalysisStore.getState();
          const payload =
            data && typeof data === "object"
              ? (data as Record<string, unknown>)
              : {};
          if (event === "result") {
            const report = (payload.data ?? payload) as AnalysisReport;
            if (
              (payload.code !== undefined && payload.code !== 200) ||
              typeof report.id !== "string" ||
              !report.id ||
              typeof report.createdAt !== "string" ||
              !report.tradeSignal ||
              !["BUY", "SELL", "HOLD"].includes(report.tradeSignal.type) ||
              ![
                report.tradeSignal.entryPrice,
                report.tradeSignal.targetPrice,
                report.tradeSignal.stopLoss,
                report.tradeSignal.holdingPeriodDays,
              ].every(Number.isFinite) ||
              (report.stockCode && report.stockCode !== state.currentStockCode)
            ) {
              store.setError("分析结果不完整，请重新分析");
            } else {
              store.applyReport(report);
            }
            disconnect();
          } else if (event === "error") {
            store.setError(
              typeof payload.message === "string"
                ? payload.message
                : "分析失败，请稍后重试",
            );
            disconnect();
          } else if (event === "complete") {
            // 兼容旧服务端 complete 先于 result 的顺序，完整报告才代表成功。
            store.handleSSEEvent({ event: "complete" });
          } else if (event === "stage" || event === "data") {
            store.handleSSEEvent({ ...payload, event } as SSEEvent);
          }
        },
        onError: () =>
          useAnalysisStore.getState().setError("连接分析服务失败，请稍后重试"),
      });
    },
    [connect, disconnect],
  );

  const stop = useCallback(() => {
    disconnect();
    useAnalysisStore.getState().clearResults();
  }, [disconnect]);

  useEffect(
    () => () => {
      disconnect();
      const state = useAnalysisStore.getState();
      if (state.isAnalyzing) state.clearResults();
    },
    [disconnect],
  );

  return { selectStock, start, stop };
}
