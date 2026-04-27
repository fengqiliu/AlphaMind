import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatNumber(num: number, decimals = 2): string {
  return num.toFixed(decimals);
}

export function formatPercent(num: number, decimals = 2): string {
  return `${num >= 0 ? "+" : ""}${num.toFixed(decimals)}%`;
}

export function formatCurrency(num: number): string {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
  }).format(num);
}

export function formatAmount(num: number): string {
  if (num >= 1_0000_0000) return `${(num / 1_0000_0000).toFixed(2)}亿`;
  if (num >= 1_0000) return `${(num / 1_0000).toFixed(2)}万`;
  return num.toFixed(2);
}

export function formatDate(date: string | Date): string {
  const d = typeof date === "string" ? new Date(date) : date;
  return d.toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatTime(date: string | Date): string {
  const d = typeof date === "string" ? new Date(date) : date;
  return d.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function getChangeColor(change: number): string {
  if (change > 0) return "text-red-500";
  if (change < 0) return "text-green-500";
  return "text-gray-500";
}

export function getChangeBgColor(change: number): string {
  if (change > 0) return "bg-red-500/10";
  if (change < 0) return "bg-green-500/10";
  return "bg-gray-500/10";
}

export function truncate(str: string, length: number): string {
  if (str.length <= length) return str;
  return `${str.slice(0, length)}...`;
}
