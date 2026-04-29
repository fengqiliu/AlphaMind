// Agent类型
export enum AgentType {
  MARKET = "MARKET",
  TECHNICAL = "TECHNICAL",
  SENTIMENT = "SENTIMENT",
  PORTFOLIO = "PORTFOLIO",
  BULL = "BULL",
  BEAR = "BEAR",
  NEUTRAL = "NEUTRAL",
  ARBITRATOR = "ARBITRATOR",
}

// Agent信息
export const AGENT_INFO: Record<
  AgentType,
  { name: string; description: string; icon: string }
> = {
  [AgentType.MARKET]: {
    name: "行情Agent",
    description: "实时行情、历史K线",
    icon: "TrendingUp",
  },
  [AgentType.TECHNICAL]: {
    name: "技术Agent",
    description: "MACD/RSI/KDJ分析",
    icon: "LineChart",
  },
  [AgentType.SENTIMENT]: {
    name: "舆情Agent",
    description: "股吧新闻舆情",
    icon: "MessageSquare",
  },
  [AgentType.PORTFOLIO]: {
    name: "投资经理",
    description: "综合决策与风控",
    icon: "Briefcase",
  },
  [AgentType.BULL]: {
    name: "多头",
    description: "看多分析",
    icon: "ArrowUpCircle",
  },
  [AgentType.BEAR]: {
    name: "空头",
    description: "看空分析",
    icon: "ArrowDownCircle",
  },
  [AgentType.NEUTRAL]: {
    name: "中立",
    description: "客观分析",
    icon: "MinusCircle",
  },
  [AgentType.ARBITRATOR]: {
    name: "仲裁官",
    description: "综合裁决",
    icon: "Scale",
  },
};

// 交易信号类型
export enum SignalType {
  BUY = "BUY",
  SELL = "SELL",
  HOLD = "HOLD",
}

// 置信度等级
export enum ConfidenceLevel {
  HIGH = "HIGH",
  MEDIUM = "MEDIUM",
  LOW = "LOW",
}

// 辩论立场
export enum DebatePosition {
  BULLISH = "BULLISH",
  BEARISH = "BEARISH",
  NEUTRAL = "NEUTRAL",
}

// 策略类型
export enum StrategyType {
  CONSERVATIVE = "conservative",
  AGGRESSIVE = "aggressive",
  BALANCED = "balanced",
}

// 分析模式
export enum AnalysisMode {
  PIPELINE = "pipeline",
  DEBATE = "debate",
}

// K线数据
export interface KLineData {
  dates: string[];
  klines: [number, number, number, number][];
  volumes: number[];
  ma5: number[];
  ma10: number[];
  ma20: number[];
  ma60: number[];
}

// 行情数据
export interface MarketData {
  stockCode: string;
  stockName: string;
  currentPrice: number;
  change: number;
  changePercent: number;
  open: number;
  high: number;
  low: number;
  volume: number;
  amount: number;
  turnoverRate: number;
  pe: number;
  pb: number;
  marketCap: number;
  updateTime: string;
  // K线数据
  klineDates?: string[];
  klines?: [number, number, number, number][]; // [open, close, low, high]
  klineVolumes?: number[];
  ma5?: (number | null)[];
  ma10?: (number | null)[];
  ma20?: (number | null)[];
  ma60?: (number | null)[];
}

// 技术指标
export interface TechnicalIndicators {
  macd: { dif: number; dea: number; histogram: number };
  rsi: { rsi6: number; rsi12: number; rsi24: number };
  kdj: { k: number; d: number; j: number };
  bollinger: { upper: number; middle: number; lower: number };
  technicalScore: number;
}

// 舆情数据（与后端 SentimentDataDTO 字段对应）
export interface SentimentData {
  sentimentScore: number;        // 综合评分 0~1
  sentimentTrend: string;        // 趋势描述
  positiveFactors: string[];     // 正面因素列表
  negativeFactors: string[];     // 负面因素列表
  newsCountBySource: Record<string, number>; // 各平台新闻数量
  mediaAttention: number;        // 媒体关注度 0~1
  analysisSummary: string;       // 舆情摘要
  aiSummary?: string;            // LLM 生成摘要（可选）
}

// 置信区间
export interface ConfidenceInterval {
  value: number;
  level: ConfidenceLevel;
  explanation?: string;
}

// 辩论观点
export interface DebateView {
  position: DebatePosition;
  view: string;
  reasons: string[];
  targetPrice?: number;
  upsidePotential?: string;
  confidence: ConfidenceInterval;
  keyPoints: string[];
  attackPoints?: string[];
}

// 仲裁裁决
export interface Judgment {
  finalPosition: DebatePosition;
  confidence: ConfidenceInterval;
  reasoning: string;
  voteBreakdown: Record<DebatePosition, number>;
  riskWarnings: string[];
  finalSignal: TradeSignal;
}

// 交易信号
export interface TradeSignal {
  type: SignalType;
  entryPrice: number;
  targetPrice: number;
  stopLoss: number;
  holdingPeriodDays: number;
  rationale: string;
}

// 分析报告
export interface AnalysisReport {
  id: string;
  stockCode: string;
  stockName: string;
  finalSignal: SignalType;
  confidence: ConfidenceInterval;
  tradeSignal: TradeSignal;
  marketData: MarketData;
  technicalIndicators: TechnicalIndicators;
  sentimentData: SentimentData;
  judgment: Judgment;
  createdAt: string;
}

// 聊天消息
export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  agentType?: AgentType;
  agentName?: string;
  modelUsed?: string;
  timestamp: string;
}

// SSE事件
export interface SSEEvent {
  event: "stage" | "data" | "error" | "complete" | "result";
  stage?: string;
  agentType?: string;
  message?: string;
  data?: unknown;
}

// 自选股
export interface WatchlistItem {
  stockCode: string;
  stockName: string;
  addedAt: string;
  currentPrice?: number;
  change?: number;
  changePercent?: number;
}

// 股票搜索结果
export interface StockSearchResult {
  code: string;
  name: string;
  market: string;
  industry: string;
}
