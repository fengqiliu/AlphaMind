# AlphaMind - 多 Agent 智能股票分析系统

> 基于 Spring Boot、Spring AI 与 Next.js 的股票分析系统。通过流水线 Agent 协作 + 多空辩论仲裁，为单只股票提供更完整的结构化分析结果。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-orange.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F.svg)
![Next.js](https://img.shields.io/badge/Next.js-16.2.4-blueviolet.svg)

---

## 项目简介

AlphaMind 由一组专职 Agent 共同完成股票分析：

- `MarketAgent`：采集行情与基础市场数据
- `TechnicalAgent`：计算技术指标并生成技术解读
- `SentimentAgent`：分析舆情与消息面
- `PortfolioAgent`：整合前置结果，给出交易建议
- `BullAgent` / `BearAgent` / `NeutralAgent`：从多头、空头、中立三个视角展开辩论
- `ArbitratorAgent`：综合辩论观点，形成最终裁决

系统支持两种工作模式：

1. **流水线模式**：Market → Technical → Sentiment → Portfolio
2. **辩论模式**：Bull / Bear / Neutral → Arbitrator

另外，系统支持：

- SSE 实时推送分析进度与阶段数据
- 前后端真实 API 对接（不再使用前端 mock）
- LLM 可选接入；未配置 API Key 时自动降级为模板响应
- 简易会话记忆与分析历史记录

---

## 当前状态

截至 2026-04-28，以下能力已完成并验证：

- 后端可在 **Java 17** 环境下成功编译
- 后端可通过 `dev` profile 启动，无需本地 MySQL 即可进行开发
- 前端已接入真实后端 API 与 SSE
- `/api/v1/analysis/stream` 已验证可返回阶段性 SSE 数据
- `/api/v1/stocks/search`、`/api/v1/stocks/watchlist`、`/api/v1/analysis/history`、聊天接口均已接通

---

## 技术栈

### 后端

- **Java**: 17
- **Spring Boot**: 3.4.1
- **Spring AI**: 1.0.0
- **通信**: REST + Server-Sent Events (SSE)
- **数据**: MySQL（生产可用）、Redis（可选）
- **LLM Provider**:
  - OpenAI
  - DeepSeek
  - Anthropic

### 前端

- **Next.js**: 16.2.4（App Router）
- **React**: 19.2.4
- **TypeScript**: 5
- **状态管理**: Zustand
- **HTTP 客户端**: Axios
- **图表**: ECharts
- **样式**: Tailwind CSS 4

---

## 项目结构

```text
AlphaMind/
├── backend/
│   ├── src/main/java/com/alphamind/
│   │   ├── agent/              # 多 Agent 实现
│   │   ├── config/             # Spring / AI / Converter 配置
│   │   ├── controller/         # 分析、聊天、股票接口
│   │   ├── model/              # DTO / 枚举 / 实体
│   │   ├── service/            # 编排、记忆、股票服务
│   │   └── AlphaMindApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-dev.yml
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── app/                # Next.js 页面
│   │   ├── api/                # API 客户端
│   │   ├── components/         # UI 组件
│   │   ├── hooks/              # 自定义 Hook（含 useSSE）
│   │   ├── stores/             # Zustand 状态
│   │   └── types/              # TS 类型定义
│   ├── next.config.ts
│   └── package.json
├── .env                        # 本地环境变量占位
└── README.md
```

---

## 快速开始

### 前置条件

- Node.js 18+
- npm 9+
- JDK 17+
- Maven 3.8+

> 开发模式下 **不强制要求 MySQL / Redis / LLM API Key**。

---

## 环境变量

项目根目录已提供 `.env` 占位文件，可按需填写：

```env
OPENAI_API_KEY=
DEEPSEEK_API_KEY=
ANTHROPIC_API_KEY=
OPENAI_BASE_URL=
DB_PASSWORD=
REDIS_PASSWORD=
NEXT_PUBLIC_API_BASE_URL=
```

说明：

- 若不填任何 LLM Key，系统会自动进入**模板/降级模式**
- `NEXT_PUBLIC_API_BASE_URL` 默认可留空；前端开发模式通过 `next.config.ts` 代理到后端
- `DB_PASSWORD`、`REDIS_PASSWORD` 主要用于非 dev 环境

---

## 本地运行

### 1. 启动后端（推荐 dev profile）

```bash
cd backend
mvn compile
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

默认地址：`http://localhost:8080`

`dev` profile 特点：

- 禁用 MySQL / JPA 自动配置，方便本地快速启动
- 保留 Redis 配置，但 Redis 不可用时会自动降级到本地内存
- 未配置 API Key 时仍可运行，只是使用模板输出

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`

如果 3000 端口被占用，Next.js 会自动切换到其他端口（例如 3001）。

### 3. 生产构建

后端：

```bash
cd backend
mvn clean package
```

前端：

```bash
cd frontend
npm run build
npm run start
```

---

## Agent 工作流

### 流水线模式

```text
MarketAgent
  -> TechnicalAgent
  -> SentimentAgent
  -> PortfolioAgent
```

### 辩论模式

```text
BullAgent -----┐
BearAgent -----┼--> ArbitratorAgent
NeutralAgent --┘
```

### LLM 调用策略

所有核心 Agent 统一继承 `BaseAgent`，通过 `llmCall(systemPrompt, userPrompt)` 调用模型：

- 有可用 `ChatClient`：走真实 LLM
- 无可用 `ChatClient` 或调用失败：自动回退到模板输出

这意味着：

- 本地无 Key 也能跑通完整流程
- 联调时无需先解决模型接入问题
- 产品体验与开发体验可以解耦

---

## API 概览

### 分析接口

#### 1) SSE 流式分析

```http
GET /api/v1/analysis/stream?stockCode=600519&strategy=balanced&enableDebate=true
```

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `stockCode` | string | 是 | 股票代码 |
| `stockName` | string | 否 | 股票名称，默认使用代码 |
| `strategy` | string | 否 | `CONSERVATIVE` / `BALANCED` / `AGGRESSIVE`，前端也可传小写 |
| `enableDebate` | boolean | 否 | 是否启用辩论模式 |
| `sessionId` | string | 否 | 会话 ID |

典型 SSE 片段：

```text
data: {"event":"stage","stage":"MARKET","message":"正在采集行情数据..."}

data: {"event":"data","agentType":"MARKET","data":{...}}

data: {"event":"stage","stage":"TECHNICAL","message":"正在进行技术分析..."}

event: result
data: {"code":200,"message":"分析完成","data":{...}}
```

#### 2) 同步分析

```http
POST /api/v1/analysis/analyze
Content-Type: application/json

{
  "stockCode": "600519",
  "strategy": "BALANCED",
  "enableDebate": true
}
```

#### 3) 分析历史

```http
GET /api/v1/analysis/history?stockCode=600519&limit=20
```

说明：当前历史记录保存在后端内存中，默认仅保留最近 50 条。

---

### 聊天接口

#### 创建会话

```http
POST /api/v1/chat/session?stockCode=600519&stockName=贵州茅台
```

#### 普通消息

```http
POST /api/v1/chat/message?sessionId={sessionId}&content=这只股票适合抄底吗？&agentType=PORTFOLIO
```

#### SSE 流式消息

```http
GET /api/v1/chat/stream/{sessionId}?message=怎么看当前走势？&agentType=TECHNICAL
```

#### 历史记录

```http
GET /api/v1/chat/history/{sessionId}?limit=20
```

#### 清除会话

```http
DELETE /api/v1/chat/session/{sessionId}
```

---

### 股票接口

#### 搜索股票

```http
GET /api/v1/stocks/search?query=600519
```

> 注意：参数名是 `query`，不是 `keyword`。

#### 获取股票详情

```http
GET /api/v1/stocks/{code}
```

#### 自选股管理

```http
GET    /api/v1/stocks/watchlist?userId=default
POST   /api/v1/stocks/watchlist/{code}?userId=default
DELETE /api/v1/stocks/watchlist/{code}?userId=default
```

---

## 前端说明

前端已完成从 mock 到真实后端的切换：

- `src/app/page.tsx`：接入真实分析 SSE
- `src/app/chat/page.tsx`：接入真实聊天流
- `src/app/watchlist/page.tsx`：接入真实 watchlist API
- `src/app/history/page.tsx`：接入真实分析历史 API
- `src/hooks/useSSE.ts`：统一封装 `EventSource`
- `next.config.ts`：配置 `/api/:path*` 代理到 `http://localhost:8080/api/:path*`

这意味着前端本地开发时：

- 浏览器请求 `/api/v1/*`
- Next.js 自动转发到后端 8080
- 不需要手动处理跨域

---

## 策略类型

| 策略 | 枚举值 | 仓位 | 止损 |
| --- | --- | --- | --- |
| 保守 | `CONSERVATIVE` | 30% | -5% |
| 平衡 | `BALANCED` | 50% | -7% |
| 激进 | `AGGRESSIVE` | 80% | -10% |

后端通过 `StrategyTypeConverter` 支持大小写不敏感转换，例如：

- `BALANCED`
- `balanced`

都可以正常解析。

---

## 开发建议

### 新增 Agent

1. 继承 `BaseAgent`
2. 实现 `analyze()`、`chat()`、`getSystemPrompt()`
3. 在 `PipelineOrchestrator` 或 `ChatController` 中接入
4. 使用 `llmCall()` + fallback 模式，保证无 Key 也能开发

### 接入真实数据源

目前股票/行情数据仍偏示例化；后续可考虑接入：

- Tushare
- AKShare
- 东方财富公开接口
- 券商研究报告 / 新闻聚合源

### 已知限制

- `analysis/history` 当前为**进程内内存历史**，重启后会丢失
- `dev` profile 不启用 JPA，因此不依赖 MySQL
- Redis 不可用时，会退回本地内存
- 当前 SSE 输出格式为文本事件流，前端已兼容

---

## 验证方式

已验证的基本命令如下：

```bash
# 后端编译
cd backend
mvn compile

# 后端启动（开发模式）
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# 测试股票搜索
curl 'http://localhost:8080/api/v1/stocks/search?query=600519'

# 测试分析流
curl --max-time 8 'http://localhost:8080/api/v1/analysis/stream?stockCode=600519&strategy=balanced&enableDebate=false'
```

---

## License

MIT License

---

## 项目链接

- GitHub: `https://github.com/fengqiliu/AlphaMind`
- Issues: `https://github.com/fengqiliu/AlphaMind/issues`

---

## 免责声明

本项目仅供学习、研究与工程实践演示使用，不构成任何投资建议。市场有风险，决策需谨慎。
