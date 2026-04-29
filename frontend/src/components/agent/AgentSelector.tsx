"use client";

import { cn } from "@/utils";
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
} from "lucide-react";

interface AgentSelectorProps {
  selected: AgentType;
  onChange: (agent: AgentType) => void;
  showPipeline?: boolean;
  showDebate?: boolean;
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

const agentGradients: Record<string, string> = {
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
  [AgentType.MARKET]: "rgba(59, 130, 246, 0.3)",
  [AgentType.TECHNICAL]: "rgba(139, 92, 246, 0.3)",
  [AgentType.SENTIMENT]: "rgba(249, 115, 22, 0.3)",
  [AgentType.PORTFOLIO]: "var(--accent-glow)",
  [AgentType.BULL]: "var(--bullish-glow)",
  [AgentType.BEAR]: "var(--bearish-glow)",
  [AgentType.NEUTRAL]: "var(--neutral-glow)",
  [AgentType.ARBITRATOR]: "rgba(234, 179, 8, 0.3)",
};

export function AgentSelector({
  selected,
  onChange,
  showPipeline = true,
  showDebate = true,
  className,
}: AgentSelectorProps) {
  const pipelineAgents = [
    AgentType.MARKET,
    AgentType.TECHNICAL,
    AgentType.SENTIMENT,
    AgentType.PORTFOLIO,
  ];
  const debateAgents = [
    AgentType.BULL,
    AgentType.BEAR,
    AgentType.NEUTRAL,
    AgentType.ARBITRATOR,
  ];

  return (
    <div className={cn("space-y-4", className)}>
      {showPipeline && (
        <div>
          <div className="text-[10px] text-[var(--text-muted)] mb-3 font-mono tracking-wider flex items-center gap-2">
            <span className="w-1 h-4 bg-[var(--accent)] rounded-full" />
            PIPELINE MODE
          </div>
          <div className="flex flex-wrap gap-2">
            {pipelineAgents.map((agent) => {
              const info = AGENT_INFO[agent];
              const isSelected = selected === agent;
              const gradient = agentGradients[agent];
              const glow = agentGlow[agent];

              return (
                <button
                  key={agent}
                  onClick={() => onChange(agent)}
                  className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded-xl text-sm transition-all duration-300",
                    isSelected
                      ? ["bg-gradient-to-r text-white shadow-lg", gradient]
                      : "bg-[var(--bg-tertiary)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]",
                  )}
                  style={isSelected ? { boxShadow: `0 0 20px ${glow}` } : {}}
                >
                  {isSelected ? (
                    <span className={`bg-white/20 p-1 rounded-lg`}>
                      {agentIcons[agent]}
                    </span>
                  ) : (
                    <span className="text-[var(--text-muted)]">
                      {agentIcons[agent]}
                    </span>
                  )}
                  <span className="font-medium">{info.name}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {showDebate && (
        <div>
          <div className="text-[10px] text-[var(--text-muted)] mb-3 font-mono tracking-wider flex items-center gap-2">
            <span className="w-1 h-4 bg-[var(--neutral)] rounded-full" />
            DEBATE MODE
          </div>
          <div className="flex flex-wrap gap-2">
            {debateAgents.map((agent) => {
              const info = AGENT_INFO[agent];
              const isSelected = selected === agent;
              const glow = agentGlow[agent];

              return (
                <button
                  key={agent}
                  onClick={() => onChange(agent)}
                  className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded-xl text-sm transition-all duration-300",
                    isSelected
                      ? "bg-gradient-to-r text-white shadow-lg"
                      : "bg-[var(--bg-tertiary)] text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]",
                  )}
                  style={isSelected ? { boxShadow: `0 0 20px ${glow}` } : {}}
                >
                  {isSelected ? (
                    <span className="bg-white/20 p-1 rounded-lg">
                      {agentIcons[agent]}
                    </span>
                  ) : (
                    <span className="text-[var(--text-muted)]">
                      {agentIcons[agent]}
                    </span>
                  )}
                  <span className="font-medium">{info.name}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="text-xs text-[var(--text-muted)] font-mono bg-[var(--bg-tertiary)] p-2 rounded-lg">
        使用 <code className="text-[var(--accent)]">@Agent名称</code>{" "}
        在消息中直接指定
      </div>
    </div>
  );
}
