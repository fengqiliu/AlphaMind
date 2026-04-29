import "./globals.css";
import type { Metadata } from "next";
import { AppShell } from "@/components/layout/AppShell";

export const metadata: Metadata = {
  title: "AlphaMind",
  description: "多 Agent 智能股票分析系统",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="grid-bg noise-overlay">
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
