# AlphaMind - 智能股票分析系统

> 基于 Spring AI 的多 Agent 智能股票分析系统，通过专业 Agent 协同与多模型辩论机制，实现更全面、更客观的投资决策支持。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-orange.svg)
![Next.js](https://img.shields.io/badge/Next.js-16-blueviolet.svg)

---

## 核心特性

| 特性           | 描述                                                                 |
| -------------- | -------------------------------------------------------------------- |
| **流水线模式** | 四个专业 Agent 串并行协作：行情采集 → 技术分析 → 舆情分析 → 综合决策 |
| **辩论模式**   | 多 Agent 多空对抗辩论，仲裁官综合裁决，减少单模型偏见                |
| **记忆系统**   | 自动召回历史分析结论，保持上下文连贯                                 |
| **流式输出**   | SSE 实时推送分析进度，提升用户体验                                   |
| **弹性降级**   | 多 LLM 热切换与熔断机制，保障系统稳定性                              |

---

## 技术栈

### 后端

- **框架**: Spring Boot 3.4 + Spring AI 1.0
- **数据库**: MySQL 8.0 + Redis 7.x
- **LLM Providers**: OpenAI GPT-4、DeepSeek、Anthropic Claude
- **实时通信**: Server-Sent Events (SSE)

### 前端

- **框架**: Next.js 16 (App Router)
- **UI**: Tailwind CSS 4 + Lucide Icons
- **状态管理**: Zustand
- **图表**: ECharts

---

## 项目结构

```
AlphaMind/
├── frontend/                    # Next.js 前端
│   ├── src/
│   │   ├── app/              # 页面路由
│   │   ├── components/       # React 组件
│   │   │   ├── agent/        # Agent 相关组件
│   │   │   ├── analysis/     # 分析结果组件
│   │   │   ├── chart/        # 图表组件
│   │   │   └── common/       # 通用组件
│   │   ├── stores/           # Zustand 状态
│   │   ├── api/              # API 客户端
│   │   ├── types/            # TypeScript 类型
│   │   └── utils/            # 工具函数
│   └── package.json
│
└── backend/                    # Spring Boot 后端
    ├── src/main/java/com/alphamind/
    │   ├── agent/             # Agent 层
    │   │   ├── BaseAgent.java
    │   │   ├── MarketAgent.java      # 行情 Agent
    │   │   ├── TechnicalAgent.java    # 技术 Agent
    │   │   ├── SentimentAgent.java   # 舆情 Agent
    │   │   ├── PortfolioAgent.java    # 投资 Agent
    │   │   ├── BullAgent.java        # 多头辩手
    │   │   ├── BearAgent.java        # 空头辩手
    │   │   ├── NeutralAgent.java     # 中立分析
    │   │   └── ArbitratorAgent.java   # 仲裁官
    │   ├── controller/        # REST API
    │   ├── service/           # 业务服务
    │   ├── model/             # 数据模型
    │   └── config/            # 配置类
    └── pom.xml
```

---

## 快速开始

### 前置条件

- Node.js 18+
- JDK 21+
- Maven 3.8+
- Redis 7.x (可选，用于会话记忆)

### 1. 克隆项目

```bash
git clone <repository-url>
cd AlphaMind
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将运行在 `http://localhost:3000`

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端将运行在 `http://localhost:8080`

### 4. 配置 LLM API Key

在 `backend/src/main/resources/application.yml` 中配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:your-api-key}
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:your-api-key}
```

或设置环境变量：

```bash
export OPENAI_API_KEY=your-key
export DEEPSEEK_API_KEY=your-key
```

---

## Agent 系统

### 流水线 Agent

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   Market    │ ──▶ │  Technical   │ ──▶ │  Sentiment  │ ──▶ │  Portfolio    │
│   Agent     │     │    Agent     │     │    Agent    │     │    Agent      │
│  (行情采集)  │     │  (技术分析)   │     │  (舆情分析)  │     │  (投资建议)   │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
```

### 辩论 Agent

```
┌─────────────┐
│  Bull Agent │  (看多)
└──────┬──────┘
       │
┌──────▼──────┐     ┌──────────────┐
│ Neutral Agent│ ──▶ │  Arbitrator  │ ──▶ 最终裁决
│   (中立)    │     │   (仲裁官)    │
└──────┬──────┘     └──────────────┘
       │
┌──────▼──────┐
│  Bear Agent │  (看空)
└─────────────┘
```

---

## API 文档

### 分析接口

#### SSE 流式分析

```http
GET /api/v1/analysis/stream?stockCode=600519&strategy=BALANCED&enableDebate=true
```

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | string | 是 | 股票代码 |
| stockName | string | 否 | 股票名称 |
| strategy | string | 否 | 策略类型: CONSERVATIVE, BALANCED, AGGRESSIVE |
| enableDebate | boolean | 否 | 是否启用辩论模式 |
| sessionId | string | 否 | 会话 ID |

**响应:** Server-Sent Events

```
event: stage
data: {"event":"stage","stage":"MARKET","message":"正在采集行情数据..."}

event: stage
data: {"event":"stage","stage":"TECHNICAL","message":"正在进行技术分析..."}

event: complete
data: {"event":"complete"}

event: result
data: {"code":200,"message":"分析完成","data":{...}}
```

#### 同步分析

```http
POST /api/v1/analysis/analyze
Content-Type: application/json

{
  "stockCode": "600519",
  "strategy": "BALANCED",
  "enableDebate": true
}
```

### 对话接口

#### 创建会话

```http
POST /api/v1/chat/session?stockCode=600519
```

#### 发送消息

```http
POST /api/v1/chat/message?sessionId={sessionId}&content={message}&agentType=PORTFOLIO
```

#### SSE 流式对话

```http
GET /api/v1/chat/stream/{sessionId}?message={message}&agentType=PORTFOLIO
```

### 股票接口

#### 搜索股票

```http
GET /api/v1/stocks/search?query=茅台
```

#### 自选股管理

```http
GET    /api/v1/stocks/watchlist?userId=default
POST   /api/v1/stocks/watchlist/{code}?userId=default
DELETE /api/v1/stocks/watchlist/{code}?userId=default
```

---

## 数据模型

### AnalysisReport

```typescript
interface AnalysisReport {
  id: string;
  stockCode: string;
  stockName: string;
  finalSignal: SignalType; // BUY | SELL | HOLD
  confidence: ConfidenceInterval;
  tradeSignal: TradeSignal;
  marketData: MarketData;
  technicalIndicators: TechnicalIndicators;
  sentimentData: SentimentData;
  judgment: Judgment; // 辩论裁决
  createdAt: Date;
}
```

### TradeSignal

```typescript
interface TradeSignal {
  type: SignalType;
  entryPrice: number;
  targetPrice: number;
  stopLoss: number;
  holdingPeriodDays: number;
  rationale: string;
}
```

### Judgment

```typescript
interface Judgment {
  finalPosition: DebatePosition; // BULLISH | BEARISH | NEUTRAL
  confidence: ConfidenceInterval;
  reasoning: string;
  voteBreakdown: Record<DebatePosition, number>;
  riskWarnings: string[];
  finalSignal: TradeSignal;
}
```

---

## 配置说明

### 策略类型

| 类型                | 仓位比例 | 止损比例 | 持仓周期 |
| ------------------- | -------- | -------- | -------- |
| 保守 (CONSERVATIVE) | 30%      | -5%      | 45天     |
| 平衡 (BALANCED)     | 50%      | -7%      | 30天     |
| 激进 (AGGRESSIVE)   | 80%      | -10%     | 15天     |

### LLM 配置

支持多 Provider 热切换，按优先级顺序尝试：

```yaml
alphamind:
  llm:
    primary-provider: openai
    fallback-providers:
      - deepseek
      - anthropic
    timeout-seconds: 120
```

---

## 开发指南

### 添加新的 Agent

1. 创建 Agent 类继承 `BaseAgent`
2. 实现 `analyze()` 和 `chat()` 方法
3. 在 `PipelineOrchestrator` 中注册
4. 在 `ChatController` 中注入

```java
@Component
public class CustomAgent extends BaseAgent {

    public CustomAgent() {
        super(AgentType.CUSTOM);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        // 分析逻辑
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        // 对话逻辑
        return response;
    }

    @Override
    public String getSystemPrompt() {
        return "Agent 系统提示词...";
    }
}
```

### 添加数据源

在 Agent 中接入真实数据：

```java
// MarketAgent.java
private MarketDataDTO fetchFromTushare(String stockCode) {
    // TODO: 实现 Tushare API 调用
}

private MarketDataDTO fetchFromAKShare(String stockCode) {
    // TODO: 实现 AKShare API 调用
}
```

---

## 环境变量

| 变量              | 说明              | 必填       |
| ----------------- | ----------------- | ---------- |
| OPENAI_API_KEY    | OpenAI API Key    | 是         |
| DEEPSEEK_API_KEY  | DeepSeek API Key  | 推荐       |
| ANTHROPIC_API_KEY | Anthropic API Key | 可选       |
| DB_PASSWORD       | MySQL 密码        | 仅生产环境 |
| REDIS_PASSWORD    | Redis 密码        | 仅生产环境 |

---

## License

MIT License - see LICENSE file for details

---

## 联系方式

- 项目主页: https://github.com/yourusername/alphamind
- 问题反馈: https://github.com/yourusername/alphamind/issues

---

_免责声明: 本系统仅供学习和研究使用，不构成任何投资建议。投资有风险，决策需谨慎。_
