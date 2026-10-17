import type { MarketData } from "@/types";
import { formatDate } from "@/utils";

export function MarketOverview({ marketData }: { marketData: MarketData }) {
  return (
    <div className="glass-card p-5 animate-enter delay-200">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <span className="w-1.5 h-5 bg-[var(--accent)] rounded-full" />
          市场行情
        </h2>
        <div className="flex flex-wrap items-center gap-2 text-xs font-mono">
          {marketData.dataStatus && (
            <span
              className={
                marketData.stale
                  ? "text-[var(--neutral)]"
                  : "text-[var(--bullish)]"
              }
            >
              {marketData.dataStatus === "LIVE"
                ? "实时数据"
                : marketData.dataStatus === "FRESH_CACHE"
                  ? "新鲜缓存"
                  : marketData.dataStatus === "STALE_CACHE"
                    ? "过期缓存"
                    : "未知状态"}
              {marketData.dataSource ? ` · ${marketData.dataSource}` : ""}
            </span>
          )}
          <span className="text-[var(--text-muted)]">
            {formatDate(marketData.updateTime)}
          </span>
        </div>
      </div>
      {marketData.dataWarning && (
        <div className="mb-4 rounded-lg border border-[var(--neutral)]/30 bg-[var(--neutral)]/10 px-3 py-2 text-xs text-[var(--neutral)]">
          {marketData.dataWarning}
        </div>
      )}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <MarketStat label="当前价" value={marketData.currentPrice} />
        <MarketStat
          label="涨跌幅"
          value={marketData.changePercent}
          format="percent"
          color
        />
        <MarketStat label="成交量" value={marketData.volume} format="amount" />
        <MarketStat
          label="换手率"
          value={marketData.turnoverRate}
          format="percent"
        />
        <MarketStat
          label="市值"
          value={marketData.marketCap}
          format="marketCap"
        />
        <MarketStat label="市盈率" value={marketData.pe} />
        <MarketStat label="市净率" value={marketData.pb} />
        <MarketStat label="当日最高" value={marketData.high} />
      </div>
    </div>
  );
}

function MarketStat({
  label,
  value,
  format,
  color,
}: {
  label: string;
  value: number | null;
  format?: "percent" | "amount" | "marketCap";
  color?: boolean;
}) {
  const formatValue = () => {
    if (value == null) return "未提供";
    if (format === "percent")
      return `${color && value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
    if (format === "amount")
      return value >= 1e8
        ? `${(value / 1e8).toFixed(2)}亿`
        : `${(value / 1e4).toFixed(2)}万`;
    if (format === "marketCap")
      return value >= 1e12
        ? `${(value / 1e12).toFixed(2)}万亿`
        : `${(value / 1e8).toFixed(2)}亿`;
    return value.toFixed(2);
  };

  const getColorClass = () => {
    if (!color || value == null) return "text-[var(--text-primary)]";
    return value >= 0 ? "text-[var(--bullish)]" : "text-[var(--bearish)]";
  };

  return (
    <div className="p-3 bg-[var(--bg-tertiary)] rounded-xl">
      <div className="text-xs text-[var(--text-muted)] mb-1 font-mono">
        {label}
      </div>
      <div className={`text-lg font-bold font-mono ${getColorClass()}`}>
        {formatValue()}
      </div>
    </div>
  );
}
