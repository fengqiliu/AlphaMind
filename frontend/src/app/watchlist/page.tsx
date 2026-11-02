"use client";

import { useEffect, useState } from "react";
import { StockSearch } from "@/components/common/StockSearch";
import { Button } from "@/components/common/Button";
import { getWatchlist, addToWatchlist, removeFromWatchlist } from "@/api/client";
import type { StockSearchResult, WatchlistItem } from "@/types";
import {
  Star,
  Trash2,
  TrendingUp,
  TrendingDown,
  Loader2,
  Plus,
} from "lucide-react";
import { cn, formatPercent, formatNumber } from "@/utils";

export default function WatchlistPage() {
  const [items, setItems] = useState<WatchlistItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  // 加载失败替换列表并提供重试；添加/删除失败只在列表上方提示，不隐藏已有自选股
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const getMarketName = (code: string) => {
    if (code.startsWith("688")) return "科创板";
    if (code.startsWith("6")) return "上海A股";
    if (code.startsWith("300") || code.startsWith("301")) return "创业板";
    if (code.startsWith("0") || code.startsWith("2")) return "深圳A股";
    if (code.startsWith("8") || code.startsWith("4")) return "北交所";
    return "A股";
  };

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setIsLoading(true);
      setLoadError(null);
      try {
        const data = await getWatchlist();
        if (!cancelled) setItems(data);
      } catch {
        if (!cancelled) setLoadError("加载自选股失败，请检查后端服务后重试");
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [attempt]);

  const handleAddStock = async (stock: StockSearchResult) => {
    setActionError(null);
    try {
      await addToWatchlist(stock.code);
      const newItem: WatchlistItem = {
        stockCode: stock.code,
        stockName: stock.name,
        addedAt: new Date().toISOString(),
      };
      setItems((prev) => [
        ...prev.filter((i) => i.stockCode !== stock.code),
        newItem,
      ]);
    } catch {
      setActionError(`添加 ${stock.name} 失败，请稍后重试`);
    }
  };

  const handleRemoveStock = async (stockCode: string) => {
    setActionError(null);
    try {
      await removeFromWatchlist(stockCode);
      setItems((prev) => prev.filter((i) => i.stockCode !== stockCode));
    } catch {
      setActionError("删除失败，请稍后重试");
    }
  };

  return (
    <div className="space-y-6 px-4 py-6 sm:px-7 lg:px-9 lg:py-8">
      {/* Header */}
      <header className="flex flex-wrap items-center justify-between gap-4 animate-enter">
        <div>
          <p className="mb-2 text-[11px] font-medium text-[var(--accent)]">
            关注列表
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">自选股</h1>
          <p className="mt-2 text-sm text-[var(--text-secondary)]">
            收藏关注的股票，随时发起一次新的分析。
          </p>
        </div>
        <span className="inline-flex shrink-0 items-center gap-2 whitespace-nowrap rounded-full border border-[var(--border)] bg-white px-4 py-2 text-xs text-[var(--text-secondary)]">
          <Star className="h-3.5 w-3.5 text-[var(--neutral)]" />
          {items.length} 只自选
        </span>
      </header>

      {/* Add Stock */}
      <div className="glass-card-glow p-5 animate-enter delay-100">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 bg-gradient-to-br from-[var(--neutral)] to-[var(--bullish)] rounded-xl flex items-center justify-center">
            <Plus className="w-5 h-5 text-[var(--bg-primary)]" />
          </div>
          <div>
            <h2 className="text-lg font-semibold">添加自选股</h2>
            <p className="text-sm text-[var(--text-muted)]">
              搜索并添加关注的股票
            </p>
          </div>
        </div>
        <StockSearch
          onSelect={handleAddStock}
          placeholder="搜索股票代码或名称..."
        />
      </div>

      {actionError && (
        <p
          role="alert"
          className="rounded-xl bg-[var(--bearish-glow)] p-3 text-sm text-[var(--bearish)]"
        >
          {actionError}
        </p>
      )}

      {/* Watchlist */}
      <div className="glass-card overflow-hidden animate-enter delay-200">
        <div className="p-4 border-b border-[var(--border)] flex items-center justify-between">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Star className="w-5 h-5 text-[var(--neutral)]" />
            我的自选股
          </h2>
        </div>

        {isLoading ? (
          <div className="p-8 flex items-center justify-center">
            <Loader2 className="w-6 h-6 text-[var(--accent)] animate-spin" />
          </div>
        ) : loadError ? (
          <div role="alert" className="flex flex-col items-center gap-3 p-8 text-center">
            <p className="text-sm text-[var(--bearish)]">{loadError}</p>
            <Button variant="outline" size="sm" onClick={() => setAttempt((value) => value + 1)}>
              重新加载
            </Button>
          </div>
        ) : items.length === 0 ? (
          <div className="p-12 text-center">
            <div className="w-16 h-16 mx-auto mb-4 bg-[var(--bg-tertiary)] rounded-2xl flex items-center justify-center">
              <Star className="w-8 h-8 text-[var(--text-muted)]" />
            </div>
            <p className="text-[var(--text-secondary)] mb-2">暂无自选股</p>
            <p className="text-sm text-[var(--text-muted)]">
              搜索并添加感兴趣的股票
            </p>
          </div>
        ) : (
          <div className="divide-y divide-[var(--border)]">
            {items.map((item) => {
              const isUp = (item.change || 0) >= 0;
              return (
                <div
                  key={item.stockCode}
                  className="p-4 hover:bg-[var(--bg-tertiary)] transition-colors flex items-center gap-4 group"
                >
                  {/* Stock Icon */}
                  <div
                    className={cn(
                      "w-12 h-12 rounded-xl flex items-center justify-center font-bold text-sm",
                      isUp
                        ? "bg-[var(--bullish-glow)] text-[var(--bullish)]"
                        : "bg-[var(--bearish-glow)] text-[var(--bearish)]",
                    )}
                  >
                    {item.stockCode.slice(0, 3)}
                  </div>

                  {/* Stock Info */}
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-[var(--text-primary)]">
                        {item.stockName}
                      </span>
                      <span className="text-xs text-[var(--text-muted)] font-mono">
                        {item.stockCode}
                      </span>
                    </div>
                    <div className="text-xs text-[var(--text-muted)] mt-1 flex items-center gap-1">
                      {getMarketName(item.stockCode)} · 主板
                      {item.dataSource && (
                        <span
                          className={cn(
                            "font-mono",
                            item.stale
                              ? "text-[var(--neutral)]"
                              : "text-[var(--bullish)]",
                          )}
                        >
                          · {item.stale ? "过期缓存" : "实时"} {item.dataSource}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Price */}
                  <div className="text-right mr-4">
                    <div className="font-mono font-bold text-lg text-[var(--text-primary)]">
                      {formatNumber(item.currentPrice || 0)}
                    </div>
                    <div
                      className={cn(
                        "flex items-center justify-end gap-1 text-sm font-mono",
                        isUp
                          ? "text-[var(--bullish)]"
                          : "text-[var(--bearish)]",
                      )}
                    >
                      {isUp ? (
                        <TrendingUp className="w-4 h-4" />
                      ) : (
                        <TrendingDown className="w-4 h-4" />
                      )}
                      {formatPercent(item.changePercent || 0)}
                    </div>
                  </div>

                  {/* Remove Button */}
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveStock(item.stockCode)}
                    className="opacity-0 group-hover:opacity-100 transition-opacity text-[var(--text-muted)] hover:text-[var(--bearish)] hover:bg-[var(--bearish-glow)]"
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
