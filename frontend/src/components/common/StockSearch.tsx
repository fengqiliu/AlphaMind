"use client";

import { useState, useEffect, useRef } from "react";
import { Search, X, Loader2 } from "lucide-react";
import { cn } from "@/utils";
import { searchStocks } from "@/api/client";
import type { StockSearchResult } from "@/types";

interface StockSearchProps {
  onSelect: (stock: StockSearchResult) => void;
  placeholder?: string;
  className?: string;
}

export function StockSearch({
  onSelect,
  placeholder = "搜索股票代码或名称...",
  className,
}: StockSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<StockSearchResult[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!query.trim()) {
      debounceRef.current = setTimeout(() => setResults([]), 0);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      setIsLoading(true);
      try {
        const data = await searchStocks(query);
        setResults(data);
      } catch (e) {
        console.error("Search error:", e);
        setResults([]);
      } finally {
        setIsLoading(false);
      }
    }, 300);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  const handleSelect = (stock: StockSearchResult) => {
    onSelect(stock);
    setQuery("");
    setResults([]);
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          placeholder={placeholder}
          className="w-full h-12 pl-11 pr-11 rounded-xl bg-[var(--bg-tertiary)] border border-[var(--border)] text-sm focus:outline-none focus:border-[var(--accent)] transition-all font-mono placeholder:text-[var(--text-muted)]"
        />
        {query && (
          <button
            onClick={() => {
              setQuery("");
              setResults([]);
            }}
            className="absolute right-4 top-1/2 -translate-y-1/2 text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors"
          >
            {isLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <X className="w-4 h-4" />
            )}
          </button>
        )}
      </div>

      {isOpen && (query.trim().length > 0 || results.length > 0) && (
        <div className="absolute top-full left-0 right-0 mt-2 glass-card-glow rounded-xl max-h-72 overflow-auto z-50">
          {isLoading && results.length === 0 ? (
            <div className="p-4 text-center text-sm text-[var(--text-muted)] font-mono">
              <Loader2 className="w-4 h-4 animate-spin inline mr-2" />
              搜索中...
            </div>
          ) : results.length === 0 && query.trim() ? (
            <div className="p-4 text-center text-sm text-[var(--text-muted)] font-mono">
              未找到相关股票
            </div>
          ) : (
            results.map((stock, index) => (
              <button
                key={stock.code}
                onClick={() => handleSelect(stock)}
                className={cn(
                  "w-full px-4 py-3 text-left transition-colors flex items-center gap-3",
                  "hover:bg-[var(--accent-subtle)]",
                  index < results.length - 1 &&
                    "border-b border-[var(--border)]",
                )}
              >
                <div className="w-10 h-10 bg-[var(--bg-tertiary)] rounded-lg flex items-center justify-center">
                  <span className="font-mono font-bold text-xs text-[var(--accent)]">
                    {stock.code.slice(0, 3)}
                  </span>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-[var(--text-primary)]">
                      {stock.name}
                    </span>
                    <span className="text-xs text-[var(--text-muted)] font-mono">
                      {stock.code}
                    </span>
                  </div>
                  <span className="text-xs text-[var(--text-muted)]">
                    {stock.industry}
                  </span>
                </div>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
