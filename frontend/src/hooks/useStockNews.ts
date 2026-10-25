import { useEffect, useState } from "react";
import { getStockNews } from "@/api/client";
import type { StockNews } from "@/types";

/**
 * 选中股票后拉取最新资讯（真实新闻源）。返回 null 表示新闻源暂不可用——
 * 不显示占位新闻，也不阻塞分析流程。
 */
export function useStockNews(stockCode: string | null | undefined) {
  const [news, setNews] = useState<StockNews | null>(null);
  const [isUnavailable, setIsUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      // 先清空，避免新标的的请求返回前仍显示上一只股票的新闻
      setNews(null);
      setIsUnavailable(false);
      if (!stockCode) return;
      try {
        const result = await getStockNews(stockCode);
        if (!cancelled) {
          setNews(result.articles.length > 0 ? result : null);
          setIsUnavailable(result.articles.length === 0);
        }
      } catch {
        if (!cancelled) {
          setNews(null);
          setIsUnavailable(true);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [stockCode]);

  return { news, isUnavailable };
}
