"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Brain,
  Settings,
} from "lucide-react";
import { APP_NAV_ITEMS } from "@/config/navigation";
import { cn } from "@/utils";

const MARKET_OVERVIEW = [
  { label: "上证指数", value: "3,268.45", change: "+0.82%", positive: true },
  { label: "深证成指", value: "10,856.32", change: "+1.12%", positive: true },
  { label: "创业板", value: "2,186.78", change: "-0.34%", positive: false },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 min-h-screen bg-gradient-to-b from-[var(--bg-secondary)] to-[var(--bg-primary)] border-r border-[var(--border)] flex flex-col fixed left-0 top-0 z-50">
      {/* Logo Section */}
      <div className="p-5 border-b border-[var(--border)]">
        <div className="flex items-center gap-3">
          <div className="relative">
            <div className="w-11 h-11 bg-gradient-to-br from-[var(--accent)] to-[#00ff88] rounded-xl flex items-center justify-center shadow-lg shadow-[var(--accent-glow)]">
              <Brain
                className="w-6 h-6 text-[var(--bg-primary)]"
                strokeWidth={2.5}
              />
            </div>
            <div className="absolute -top-1 -right-1 w-3 h-3 bg-[var(--bullish)] rounded-full pulse-live" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">
              <span className="gradient-text">Alpha</span>
              <span className="text-[var(--text-primary)]">Mind</span>
            </h1>
            <p className="text-[10px] text-[var(--text-muted)] font-mono tracking-widest uppercase">
              Multi-Agent Trading
            </p>
          </div>
        </div>
      </div>

      {/* Status Bar */}
      <div className="px-4 py-3 border-b border-[var(--border)]">
        <div className="flex items-center justify-between text-xs font-mono">
          <span className="text-[var(--text-muted)]">STATUS</span>
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 bg-[var(--bullish)] rounded-full pulse-live" />
            <span className="text-[var(--bullish)]">ONLINE</span>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-3 space-y-1">
        {APP_NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden",
                  isActive
                    ? "bg-gradient-to-r from-[var(--accent-subtle)] to-transparent text-[var(--accent)]"
                    : "text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]",
                )}
              >
                {isActive && (
                  <>
                    <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-[var(--accent)] rounded-r-full shadow-lg shadow-[var(--accent-glow)]" />
                    <div className="absolute inset-0 bg-gradient-to-r from-[var(--accent-glow)] to-transparent opacity-20" />
                  </>
                )}

                <div
                  className={cn(
                    "transition-colors",
                    isActive
                      ? "text-[var(--accent)]"
                      : "text-[var(--text-muted)] group-hover:text-[var(--text-primary)]",
                  )}
                >
                  <Icon className="w-5 h-5" />
                </div>
                <div className="flex-1">
                  <div className="font-medium text-sm">{item.label}</div>
                  <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider">
                    {item.desc}
                  </div>
                </div>
                {isActive && (
                  <div className="w-2 h-2 bg-[var(--accent)] rounded-full pulse-live" />
                )}
              </Link>
            );
          })}
      </nav>

      {/* Market Quick Stats */}
      <div className="mx-3 mb-3 p-3 glass-card">
        <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-2">
          MARKET OVERVIEW
        </div>
        <div className="space-y-2">
          {MARKET_OVERVIEW.map((item) => (
            <QuickStat
              key={item.label}
              label={item.label}
              value={item.value}
              change={item.change}
              positive={item.positive}
            />
          ))}
        </div>
      </div>

      {/* Footer */}
      <div className="p-3 border-t border-[var(--border)]">
        <button className="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)] transition-all duration-300 w-full group">
          <Settings className="w-5 h-5 group-hover:rotate-45 transition-transform duration-300" />
          <span>系统设置</span>
        </button>
      </div>
    </aside>
  );
}

function QuickStat({
  label,
  value,
  change,
  positive,
}: {
  label: string;
  value: string;
  change: string;
  positive: boolean;
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-xs text-[var(--text-muted)]">{label}</span>
      <div className="flex items-center gap-2">
        <span className="text-xs font-mono text-[var(--text-primary)]">
          {value}
        </span>
        <span
          className={cn(
            "text-[10px] font-mono",
            positive ? "text-[var(--bullish)]" : "text-[var(--bearish)]",
          )}
        >
          {change}
        </span>
      </div>
    </div>
  );
}
