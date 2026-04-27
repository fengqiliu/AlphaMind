"use client";

import { cn, formatTime } from "@/utils";
import type { ChatMessage as ChatMessageType } from "@/types";
import { AgentType, AGENT_INFO } from "@/types";
import {
  TrendingUp,
  LineChart,
  MessageSquare,
  Briefcase,
  ArrowUpCircle,
  ArrowDownCircle,
  MinusCircle,
  Scale,
  User,
  Bot,
} from "lucide-react";

interface AgentMessageProps {
  message: ChatMessageType;
  className?: string;
}

const agentIcons: Record<string, React.ReactNode> = {
  [AgentType.MARKET]: <TrendingUp className="w-4 h-4" />,
  [AgentType.TECHNICAL]: <LineChart className="w-4 h-4" />,
  [AgentType.SENTIMENT]: <MessageSquare className="w-4 h-4" />,
  [AgentType.PORTFOLIO]: <Briefcase className="w-4 h-4" />,
  [AgentType.BULL]: <ArrowUpCircle className="w-4 h-4" />,
  [AgentType.BEAR]: <ArrowDownCircle className="w-4 h-4" />,
  [AgentType.NEUTRAL]: <MinusCircle className="w-4 h-4" />,
  [AgentType.ARBITRATOR]: <Scale className="w-4 h-4" />,
};

const agentColors: Record<string, string> = {
  [AgentType.MARKET]: "from-[#3b82f6] to-[#60a5fa]",
  [AgentType.TECHNICAL]: "from-[#8b5cf6] to-[#a78bfa]",
  [AgentType.SENTIMENT]: "from-[#f97316] to-[#fb923c]",
  [AgentType.PORTFOLIO]: "from-[var(--accent)] to-[#00ff88]",
  [AgentType.BULL]: "from-[var(--bullish)] to-[#66ffaa]",
  [AgentType.BEAR]: "from-[var(--bearish)] to-[#ff6699]",
  [AgentType.NEUTRAL]: "from-[var(--neutral)] to-[#ffcc00]",
  [AgentType.ARBITRATOR]: "from-[#eab308] to-[#facc15]",
};

const agentGlow: Record<string, string> = {
  [AgentType.MARKET]: "var(--accent-glow)",
  [AgentType.TECHNICAL]: "rgba(139, 92, 246, 0.3)",
  [AgentType.SENTIMENT]: "rgba(249, 115, 22, 0.3)",
  [AgentType.PORTFOLIO]: "var(--accent-glow)",
  [AgentType.BULL]: "var(--bullish-glow)",
  [AgentType.BEAR]: "var(--bearish-glow)",
  [AgentType.NEUTRAL]: "var(--neutral-glow)",
  [AgentType.ARBITRATOR]: "rgba(234, 179, 8, 0.3)",
};

export function AgentMessage({ message, className }: AgentMessageProps) {
  const isUser = message.role === "user";
  const icon = isUser ? (
    <User className="w-4 h-4" />
  ) : (
    agentIcons[message.agentType || ""] || <Bot className="w-4 h-4" />
  );
  const gradientClass = isUser
    ? "from-[var(--accent)] to-[#00ff88]"
    : agentColors[message.agentType || ""] || "from-gray-500 to-gray-400";
  const glowClass = isUser
    ? "var(--accent-glow)"
    : agentGlow[message.agentType || ""] || "rgba(255,255,255,0.1)";

  const agentInfo = message.agentType ? AGENT_INFO[message.agentType] : null;

  const isJson = (str: string) => {
    try {
      const parsed = JSON.parse(str);
      return typeof parsed === "object" && parsed !== null;
    } catch {
      return false;
    }
  };

  const renderContent = () => {
    if (isJson(message.content)) {
      return (
        <pre className="text-xs bg-[var(--bg-primary)] rounded-lg p-3 overflow-x-auto whitespace-pre-wrap font-mono text-[var(--text-secondary)] border border-[var(--border)]">
          {JSON.stringify(JSON.parse(message.content), null, 2)}
        </pre>
      );
    }
    return (
      <p className="whitespace-pre-wrap leading-relaxed">{message.content}</p>
    );
  };

  return (
    <div
      className={cn(
        "flex gap-3 animate-enter",
        isUser ? "flex-row-reverse" : "",
        className,
      )}
    >
      {/* Avatar */}
      <div
        className={cn(
          "flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center text-white shadow-lg",
          `bg-gradient-to-br ${gradientClass}`,
        )}
        style={{ boxShadow: `0 0 20px ${glowClass}` }}
      >
        {icon}
      </div>

      {/* Message Content */}
      <div
        className={cn(
          "flex flex-col max-w-[75%]",
          isUser ? "items-end" : "items-start",
        )}
      >
        {/* Header */}
        <div className="flex items-center gap-2 mb-2">
          <span className="text-sm font-semibold text-[var(--text-primary)]">
            {isUser ? "你" : agentInfo?.name || "AI助手"}
          </span>
          {agentInfo && !isUser && (
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-[var(--bg-tertiary)] text-[var(--text-muted)] font-mono">
              {agentInfo.description}
            </span>
          )}
          <span className="text-xs text-[var(--text-muted)] font-mono">
            {formatTime(message.timestamp)}
          </span>
        </div>

        {/* Bubble */}
        <div
          className={cn(
            "rounded-2xl px-4 py-3 text-sm",
            isUser
              ? "bg-gradient-to-br from-[var(--accent)] to-[#00ff88] text-[var(--bg-primary)] rounded-br-md"
              : "bg-[var(--bg-tertiary)] text-[var(--text-primary)] rounded-bl-md border border-[var(--border)]",
          )}
        >
          {renderContent()}
        </div>

        {/* Model Info */}
        {message.modelUsed && (
          <span className="text-[10px] text-[var(--text-muted)] mt-1 font-mono px-1">
            via {message.modelUsed}
          </span>
        )}
      </div>
    </div>
  );
}
