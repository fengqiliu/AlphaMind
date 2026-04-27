"use client";

import { create } from "zustand";
import type { ChatMessage, AgentType, SSEEvent } from "@/types";

interface ChatState {
  sessionId: string | null;
  currentStockCode: string | null;
  messages: ChatMessage[];
  inputMessage: string;
  selectedAgent: AgentType;
  isLoading: boolean;
  loadingMessage: string;
  error: string | null;

  setSessionId: (id: string | null) => void;
  setCurrentStockCode: (code: string | null) => void;
  setInputMessage: (message: string) => void;
  setSelectedAgent: (agent: AgentType) => void;
  setIsLoading: (loading: boolean) => void;
  setLoadingMessage: (message: string) => void;
  setError: (error: string | null) => void;
  addMessage: (message: ChatMessage) => void;
  handleSSEEvent: (event: SSEEvent) => void;
  clearMessages: () => void;
  reset: () => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: null,
  currentStockCode: null,
  messages: [],
  inputMessage: "",
  selectedAgent: AgentType.PORTFOLIO,
  isLoading: false,
  loadingMessage: "",
  error: null,

  setSessionId: (id) => set({ sessionId: id }),
  setCurrentStockCode: (code) => set({ currentStockCode: code }),
  setInputMessage: (message) => set({ inputMessage: message }),
  setSelectedAgent: (agent) => set({ selectedAgent: agent }),
  setIsLoading: (loading) => set({ isLoading: loading }),
  setLoadingMessage: (message) => set({ loadingMessage: message }),
  setError: (error) => set({ error, isLoading: false }),

  addMessage: (message) =>
    set((state) => ({ messages: [...state.messages, message] })),

  handleSSEEvent: (event) => {
    switch (event.event) {
      case "stage":
        set({ loadingMessage: event.message || "" });
        break;
      case "data":
        const message: ChatMessage = {
          id: crypto.randomUUID(),
          role: "assistant",
          content:
            typeof event.data === "string"
              ? event.data
              : JSON.stringify(event.data, null, 2),
          agentType: event.stage as AgentType,
          agentName: getAgentName(event.stage as AgentType),
          timestamp: new Date().toISOString(),
        };
        set((state) => ({ messages: [...state.messages, message] }));
        break;
      case "complete":
        set({ isLoading: false });
        break;
      case "error":
        set({ error: event.message || "分析失败", isLoading: false });
        break;
    }
  },

  clearMessages: () => set({ messages: [] }),
  reset: () =>
    set({
      sessionId: null,
      currentStockCode: null,
      messages: [],
      inputMessage: "",
      selectedAgent: AgentType.PORTFOLIO,
      isLoading: false,
      loadingMessage: "",
      error: null,
    }),
}));

function getAgentName(agentType?: string): string {
  const names: Record<string, string> = {
    MARKET: "行情Agent",
    TECHNICAL: "技术Agent",
    SENTIMENT: "舆情Agent",
    PORTFOLIO: "投资经理",
    BULL: "多头",
    BEAR: "空头",
    NEUTRAL: "中立",
    ARBITRATOR: "仲裁官",
  };
  return names[agentType || ""] || "助手";
}
