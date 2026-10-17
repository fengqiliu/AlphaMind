import type { LucideIcon } from "lucide-react";
import {
  ChartLine,
  History,
  MessageSquare,
  Star,
  ShieldCheck,
} from "lucide-react";

export interface AppNavItem {
  href: string;
  label: string;
  desc: string;
  icon: LucideIcon;
}

export const APP_NAV_ITEMS: AppNavItem[] = [
  {
    href: "/investment-model",
    label: "投资模型",
    desc: "个人投资复核",
    icon: ShieldCheck,
  },
  {
    href: "/",
    label: "智能分析",
    desc: "Pipeline Analysis",
    icon: ChartLine,
  },
  {
    href: "/chat",
    label: "AI对话",
    desc: "Agent Chat",
    icon: MessageSquare,
  },
  {
    href: "/watchlist",
    label: "自选股",
    desc: "Watchlist",
    icon: Star,
  },
  {
    href: "/history",
    label: "历史记录",
    desc: "Analysis History",
    icon: History,
  },
];
