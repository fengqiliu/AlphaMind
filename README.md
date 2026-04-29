# AlphaMind - 多 Agent 智能股票分析系统

> 基于 Spring Boot、Spring AI 与 Next.js 的股票分析系统。通过流水线 Agent 协作 + 多空辩论仲裁，为单只股票提供结构化分析结果，前后端全程真实 API 对接，无 mock 数据。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-orange.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F.svg)
![Next.js](https://img.shields.io/badge/Next.js-16.2.4-blueviolet.svg)

---

## 项目简介

AlphaMind 由一组专职 Agent 共同完成股票分析：

- `MarketAgent`：通过新浪财经实时接口拉取行情数据，生成含均线的 K 线序列
- `TechnicalAgent`：采用真实公式计算 EMA / RSI / KDJ / 布林带等技术指标
- `SentimentAgent`：基于行情指标（涨跌幅、PE、换手率、量比）进行加权舆情评分
- `PortfolioAgent`：整合前置结果，生成交易建议与仓位控制方案
- `BullAgent` / `BearAgent` / `NeutralAgent`：从多头、空头、中立三个视角展开辩论
- `ArbitratorAgent`：综合辩论观点，形成最终裁决

系统支持两种工作模式：

1. **流水线模式**：Market → Technical → Sentiment → Portfolio
2. **辩论模式**：Bull / Bear / Neutral → Arbitrator

其他能力：

- SSE 实时推送分析进度与各阶段数据
- 新浪财经实时行情接入，可通过配置开关切换
- LLM 可选接入；未配置 API Key 时自动降级为模板响应
- 分析历史持久化（PostgreSQL jsonb），支持完整反序列化还原
- 会话记忆（Redis，不可用时自动降级本地内存）

---

## 当前状态

截至 2026-04-29，以下能力已完成并验证：

- 后端在 **Java 17** 环境下编译 0 错误
- 后端通过 `dev` profile 启动，无需本地 MySQL 即可开发（使用 PostgreSQL dev 库）
- 前端 Next.js build 5 条路由全部构建通过，0 TypeScript 错误
- `MarketAgent` 已接入新浪财经实时接口，K 线锚定真实现价
- `TechnicalAgent` 使用真实 EMA/RSI/KDJ/布林带公式，非随机数
- `SentimentAgent` 使用确定性加权评分（涨跌幅 40%、PE 25%、换手率 20%、量比 15%）
- 分析历史 `toDTO()` 完整还原所有 jsonb 字段及标量字段
- 前端舆情分析卡片展示评分、利好/风险因素、媒体关注度
- 前端 `SentimentData` 类型与后端 DTO 字段完全对齐
- CORS 通过配置文件管理，无硬编码通配符

---

## 技术栈

### 后端

| 组件 | 版本 / 说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.4.1 |
| Spring AI | 1.0.0 GA |
| 通信 | REST + Server-Sent Events (WebFlux Flux) |
| 数据库 | PostgreSQL（含 jsonb 字段）+ Flyway |
| 缓存 | Redis（可选，不可用时降级本地内存） |
| LLM Provider | OpenAI / DeepSeek / Anthropic |
| 行情数据 | 新浪财经实时接口（`alphamind.market.fetch-real-data` 开关控制） |

### 前端

| 组件 | 版本 / 说明 |
| --- | --- |
| Next.js | 16.2.4（App Router） |
| React | 19 |
| TypeScript | 5 |
| 状态管理 | Zustand |
| HTTP 客户端 | Axios（REST） + EventSource（SSE） |
| 图表 | ECharts |
| 样式 | Tailwind CSS 4 |

---

## 项目结构

```text
AlphaMind/
├── backend/
│   ├── src/main/java/com/alphamind/
│   │   ├── agent/              # 多 Agent 实现（BaseAgent + 7 个专职 Agent）
│   │   ├── config/             # Spring / AI / CORS / Converter 配置
│   │   ├── controller/         # 分析（SSE+同步+历史）、聊天、股票接口
│   │   ├── model/              # DTO / 枚举 / 实体（含 jsonb Entity）
│   │   ├── service/            # PipelineOrchestrator / MemoryService / StockService
│   │   └── AlphaMindApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml        # 生产配置（含环境变量占位）
│   │   └── application-dev.yml    # 开发配置（PostgreSQL dev 库、真实行情开关）
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── app/                # Next.js 页面（/ chat/ history/ watchlist/）
│   │   ├── api/                # Axios API 客户端
│   │   ├── components/         # UI 组件（analysis/ chart/ common/ agent/）
│   │   ├── stores/             # Zustand 状态（analysis / chat / watchlist）
│   │   └── types/              # TypeScript 类型定义
│   ├── next.config.ts          # /api/:path* 代理到 localhost:8080
│   └── package.json
└── README.md
```

---

## 快速开始

### 前置条件

- Node.js 18+
- npm 9+
- JDK 17+
- Maven 3.8+
- PostgreSQL（dev 环境需要，数据库名 `alphamind_dev`）

> Redis 和 LLM API Key 均为可选。无 Key 时系统自动进入模板/降级模式。

---

## 环境变量

可在项目根目录创建 `.env` 文件（后端通过 Spring 的 `${VAR:default}` 语法读取）：

```env
# LLM（至少填一个才能启用真实 AI 响应）
OPENAI_API_KEY=
DEEPSEEK_API_KEY=
ANTHROPIC_API_KEY=
OPENAI_BASE_URL=

# 数据库（生产环境）
DB_PASSWORD=

# Redis（可选，不填时自动降级本地内存）
REDIS_PASSWORD=

# CORS（默认 http://localhost:3000）
CORS_ALLOWED_ORIGINS=http://localhost:3000

# 是否拉取新浪财经真实行情（默认 false，dev profile 默认 true）
FETCH_REAL_DATA=true
```

---

## 本地运行

### 1. 启动后端（dev profile）

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

默认地址：`http://localhost:8080`

`dev` profile 特点：

- 使用 PostgreSQL `localhost:5432/alphamind_dev`（需提前建库）
- 启用真实新浪财经行情接口（`alphamind.market.fetch-real-data: true`）
- Redis 不可用时自动降级本地内存
- 无 LLM Key 时使用模板输出，流程依然走通

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`

浏览器请求 `/api/v1/*` 会由 `next.config.ts` 自动代理到后端 8080，无需手动处理跨域。

### 3. 生产构建

```bash
# 后端
cd backend && mvn clean package

# 前端
cd frontend && npm run build && npm run start
```

---

## Agent 工作流

### 流水线模式

```text
MarketAgent（新浪实时行情 + K线序列）
  -> TechnicalAgent（EMA / RSI / KDJ / 布林带）
  -> SentimentAgent（加权舆情评分）
  -> PortfolioAgent（交易建议 + 仓位控制）
```

### 辩论模式

```text
BullAgent ─────┐
BearAgent ─────┼──> ArbitratorAgent（最终裁决 + 置信度）
NeutralAgent ──┘
```

### LLM 调用策略

所有 Agent 统一继承 `BaseAgent`，通过 `llmCall(systemPrompt, userPrompt)` 调用模型：

- 有可用 `ChatClient`：走真实 LLM
- 无可用 `ChatClient` 或调用失败：自动回退到模板输出

---

## API 概览

### 分析接口

#### SSE 流式分析

```http
GET /api/v1/analysis/stream?stockCode=600519&strategy=balanced&enableDebate=false
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `stockCode` | string | 是 | 股票代码 |
| `strategy` | string | 否 | `CONSERVATIVE` / `BALANCED` / `AGGRESSIVE`（大小写均可） |
| `enableDebate` | boolean | 否 | 是否启用辩论模式 |
| `mode` | string | 否 | `PIPELINE` / `DEBATE`，优先级高于 `enableDebate` |
| `sessionId` | string | 否 | 会话 ID |

典型事件序列：

```text
data: {"event":"stage","stage":"MARKET","message":"正在采集行情数据..."}
data: {"event":"data","agentType":"MARKET","data":{...}}
data: {"event":"stage","stage":"TECHNICAL","message":"正在进行技术分析..."}
data: {"event":"data","agentType":"TECHNICAL","data":{...}}
data: {"event":"data","agentType":"SENTIMENT","data":{...}}
data: {"event":"data","agentType":"PORTFOLIO","data":{...}}
event: result
data: {"code":200,"message":"分析完成","data":{完整 AnalysisReportDTO}}
```

#### 同步分析

```http
POST /api/v1/analysis/analyze
Content-Type: application/json

{ "stockCode": "600519", "strategy": "BALANCED", "enableDebate": false }
```

#### 分析历史

```http
GET /api/v1/analysis/history?stockCode=600519&limit=20
```

> 历史记录存入 PostgreSQL，支持完整字段还原（包括 marketData / technicalIndicators / sentimentData / judgment / tradeSignal / confidence 等所有 jsonb 与标量字段）。

---

### 聊天接口

```http
# 创建会话
POST /api/v1/chat/session?stockCode=600519&stockName=贵州茅台

# 普通消息
POST /api/v1/chat/message?sessionId={id}&content=适合抄底吗？&agentType=PORTFOLIO

# SSE 流式消息
GET /api/v1/chat/stream/{sessionId}?message=怎么看当前走势？&agentType=TECHNICAL

# 历史记录
GET /api/v1/chat/history/{sessionId}?limit=20

# 清除会话
DELETE /api/v1/chat/session/{sessionId}
```

---

### 股票接口

```http
GET    /api/v1/stocks/search?query=600519          # 参数名是 query，不是 keyword
GET    /api/v1/stocks/{code}
GET    /api/v1/stocks/watchlist?userId=default
POST   /api/v1/stocks/watchlist/{code}?userId=default
DELETE /api/v1/stocks/watchlist/{code}?userId=default
```

---

## 前端页面

| 路由 | 功能 |
| --- | --- |
| `/` | 主分析页：股票搜索、策略/模式选择、SSE 实时进度、市场行情、舆情分析、K线图、技术指标、交易建议、辩论结果 |
| `/chat` | 对话页：选择 Agent、流式聊天 |
| `/history` | 历史记录：查看过往分析报告 |
| `/watchlist` | 自选股管理 |

---

## 策略类型

| 策略 | 枚举值 | 仓位 | 止损 |
| --- | --- | --- | --- |
| 保守 | `CONSERVATIVE` | 30% | -5% |
| 平衡 | `BALANCED` | 50% | -7% |
| 激进 | `AGGRESSIVE` | 80% | -10% |

---

## 开发指引

### 新增 Agent

1. 继承 `BaseAgent`，实现 `analyze()`、`chat()`、`getSystemPrompt()`
2. 在 `PipelineOrchestrator` 或 `ChatController` 中接入
3. 使用 `llmCall()` + fallback 模式，保证无 Key 也能本地开发

### 切换行情数据源

目前 `MarketAgent` 支持新浪财经实时接口。`dev` profile 默认开启：

```yaml
alphamind:
  market:
    fetch-real-data: true
```

生产环境可通过环境变量 `FETCH_REAL_DATA=true` 开启，或在 `application.yml` 中修改默认值。

### 已知限制

- 聊天历史依赖 Redis；Redis 不可用时，重启后会话内存清空
- 当前股票搜索为本地静态匹配，未接入外部股票元数据库
- 新浪财经接口为非官方公开数据，如失败会降级为合成数据

---

## 验证命令

```bash
# 后端编译
cd backend && SPRING_PROFILES_ACTIVE=dev mvn -q compile

# 后端启动
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# 前端构建
cd frontend && npm run build

# 测试股票搜索
curl 'http://localhost:8080/api/v1/stocks/search?query=600519'

# 测试 SSE 分析流
curl --max-time 10 'http://localhost:8080/api/v1/analysis/stream?stockCode=600519&strategy=balanced&enableDebate=false'
```

---

## License

MIT License

---

---
