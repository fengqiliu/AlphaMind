"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowUpRight, AudioLines, Layers3 } from "lucide-react";
import { APP_NAV_ITEMS } from "@/config/navigation";
import { cn } from "@/utils";

export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="z-40 border-b border-[var(--border)] bg-white lg:fixed lg:inset-y-0 lg:left-0 lg:flex lg:w-60 lg:flex-col lg:border-r lg:border-b-0">
      <div className="flex items-center justify-between px-5 py-5 lg:px-6 lg:py-8">
        <Link
          href="/"
          aria-label="AlphaMind 首页"
          className="flex items-center gap-2.5"
        >
          <span className="flex size-9 items-center justify-center rounded-xl bg-[var(--accent)] text-white">
            <AudioLines className="size-5" strokeWidth={2.5} />
          </span>
          <span className="text-xl font-bold tracking-tight">
            AlphaMind<span className="text-[var(--accent)]">.</span>
          </span>
        </Link>
        <span className="rounded-md bg-[var(--bg-tertiary)] px-2 py-1 text-[10px] font-medium text-[var(--text-muted)] lg:hidden">
          研究工作台
        </span>
      </div>
      <div className="hidden px-6 pb-3 text-[10px] font-semibold tracking-[0.18em] text-[var(--text-muted)] lg:block">
        研究空间
      </div>
      <nav
        aria-label="主导航"
        className="grid grid-cols-5 gap-1 px-3 pb-3 lg:block lg:space-y-1 lg:px-4"
      >
        {APP_NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group flex flex-col items-center justify-center gap-1.5 whitespace-nowrap rounded-xl px-1 py-2.5 text-[11px] font-medium transition-colors sm:flex-row sm:gap-2 sm:text-xs lg:justify-start lg:gap-3 lg:px-4 lg:py-3 lg:text-sm",
                active
                  ? "bg-[var(--accent-subtle)] text-[var(--accent)]"
                  : "text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]",
              )}
            >
              <Icon className="size-4 shrink-0 lg:size-[18px]" />
              <span>{item.label}</span>
              {active && (
                <span className="ml-auto hidden size-1.5 rounded-full bg-[var(--accent)] lg:block" />
              )}
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto hidden p-4 lg:block">
        <div className="rounded-2xl bg-[#f4f7f6] p-4">
          <Layers3 className="mb-3 size-5 text-[var(--accent)]" />
          <p className="text-sm font-semibold">让观点有据可循</p>
          <p className="mt-2 text-xs leading-5 text-[var(--text-secondary)]">
            从行情、技术到消息面，逐步形成完整的研究视角。
          </p>
          <Link
            href="/history"
            className="mt-4 inline-flex items-center gap-1 text-xs font-medium text-[var(--accent)]"
          >
            回顾历史研究
            <ArrowUpRight className="size-3.5" />
          </Link>
        </div>
        <p className="px-1 pt-5 pb-2 text-[10px] text-[var(--text-muted)]">
          AlphaMind · 多 Agent 协作研究
        </p>
      </div>
    </aside>
  );
}
