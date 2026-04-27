"use client";

import "./globals.css";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ChartLine,
  MessageSquare,
  Star,
  History,
  Settings,
  Brain,
} from "lucide-react";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="grid-bg noise-overlay">
        <div className="flex min-h-screen">
          {/* Terminal-style Sidebar */}
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
              <NavItem
                href="/"
                icon={<ChartLine />}
                label="智能分析"
                desc="Pipeline Analysis"
              />
              <NavItem
                href="/chat"
                icon={<MessageSquare />}
                label="AI对话"
                desc="Agent Chat"
              />
              <NavItem
                href="/watchlist"
                icon={<Star />}
                label="自选股"
                desc="Watchlist"
              />
              <NavItem
                href="/history"
                icon={<History />}
                label="历史记录"
                desc="Analysis History"
              />
            </nav>

            {/* Market Quick Stats */}
            <div className="mx-3 mb-3 p-3 glass-card">
              <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider mb-2">
                MARKET OVERVIEW
              </div>
              <div className="space-y-2">
                <QuickStat
                  label="上证指数"
                  value="3,268.45"
                  change="+0.82%"
                  positive
                />
                <QuickStat
                  label="深证成指"
                  value="10,856.32"
                  change="+1.12%"
                  positive
                />
                <QuickStat
                  label="创业板"
                  value="2,186.78"
                  change="-0.34%"
                  positive={false}
                />
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

          {/* Main Content */}
          <main className="flex-1 ml-64">{children}</main>
        </div>
      </body>
    </html>
  );
}

function NavItem({
  href,
  icon,
  label,
  desc,
}: {
  href: string;
  icon: React.ReactNode;
  label: string;
  desc: string;
}) {
  "use client";
  const pathname = usePathname();
  const isActive = pathname === href;

  return (
    <Link
      href={href}
      className={`
        flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden
        ${
          isActive
            ? "bg-gradient-to-r from-[var(--accent-subtle)] to-transparent text-[var(--accent)]"
            : "text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]"
        }
      `}
    >
      {/* Active indicator */}
      {isActive && (
        <>
          <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-[var(--accent)] rounded-r-full shadow-lg shadow-[var(--accent-glow)]" />
          <div className="absolute inset-0 bg-gradient-to-r from-[var(--accent-glow)] to-transparent opacity-20" />
        </>
      )}

      <div
        className={`${isActive ? "text-[var(--accent)]" : "text-[var(--text-muted)] group-hover:text-[var(--text-primary)]"} transition-colors`}
      >
        {icon}
      </div>
      <div className="flex-1">
        <div className="font-medium text-sm">{label}</div>
        <div className="text-[10px] text-[var(--text-muted)] font-mono tracking-wider">
          {desc}
        </div>
      </div>
      {isActive && (
        <div className="w-2 h-2 bg-[var(--accent)] rounded-full pulse-live" />
      )}
    </Link>
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
          className={`text-[10px] font-mono ${positive ? "text-[var(--bullish)]" : "text-[var(--bearish)]"}`}
        >
          {change}
        </span>
      </div>
    </div>
  );
}
