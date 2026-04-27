"use client";

import { create } from "zustand";
import type { WatchlistItem } from "@/types";

interface WatchlistState {
  items: WatchlistItem[];
  isLoading: boolean;
  error: string | null;

  setItems: (items: WatchlistItem[]) => void;
  addItem: (item: WatchlistItem) => void;
  removeItem: (stockCode: string) => void;
  setIsLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useWatchlistStore = create<WatchlistState>((set) => ({
  items: [],
  isLoading: false,
  error: null,

  setItems: (items) => set({ items }),

  addItem: (item) =>
    set((state) => ({
      items: [
        ...state.items.filter((i) => i.stockCode !== item.stockCode),
        item,
      ],
    })),

  removeItem: (stockCode) =>
    set((state) => ({
      items: state.items.filter((item) => item.stockCode !== stockCode),
    })),

  setIsLoading: (loading) => set({ isLoading: loading }),

  setError: (error) => set({ error }),
}));
