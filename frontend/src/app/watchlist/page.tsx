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
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    getWatchlist()
      .then(setItems)
      .catch(() => setError("加载自选股失败"))
      .finally(() => setIsLoading(false));
  }, []);

  const handleAddStock = async (stock: StockSearchResult) => {
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
      setError("添加失败，请稍后重试");
    }
  };

  const handleRemoveStock = async (stockCode: string) => {
    try {
      await removeFromWatchlist(stockCode);
      setItems((prev) => prev.filter((i) => i.stockCode !== stockCode));
    } catch {
      setError("删除失败，请稍后重试");
    }
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between animate-enter">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="gradient-text">自选股</span>
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-1 font-mono">
            PERSONAL WATCHLIST MANAGEMENT
          </p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 glass-card">
          <Star className="w-4 h-4 text-[var(--neutral)]" />
          <span className="text-xs font-mono text-[var(--text-secondary)]">
            {items.length} 只自选
          </span>
        </div>
      </div>

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
        ) : items.length === 0 ? (
          <div className="p-12 text-center">
            <div className="w-16 h-16 mx-auto mb-4 bg-[var(--bg-tertiary)] rounded-2xl flex items-center justify-center">
              <Star className="w-8 h-8 text-[var(--text-muted)]" />
            </div>
            <p className="text-[var(--text-secondary)] mb-2">暂无自选股</p>
            <p className="text-sm text-[var(--text-muted)] font-mono">
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
                    <div className="text-xs text-[var(--text-muted)] mt-1">
                      {item.stockCode.startsWith("6") ? "上海" : "深圳"} · 主板
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
