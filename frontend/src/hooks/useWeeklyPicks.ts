"use client";

import { useEffect, useState } from "react";
import { getWeeklyValuePicks } from "@/api/client";
import type { WeeklyStockRecommendation } from "@/types";

export function useWeeklyPicks() {
  const [picks, setPicks] = useState<WeeklyStockRecommendation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const result = await getWeeklyValuePicks();
        if (!cancelled) setPicks(result);
      } catch {
        if (!cancelled) setError("本周观察加载失败，请稍后重试");
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = () => {
    setIsLoading(true);
    setError(null);
    setAttempt((value) => value + 1);
  };
  return { picks, isLoading, error, retry };
}
