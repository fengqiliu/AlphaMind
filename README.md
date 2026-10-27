# AlphaMind - 多 Agent 智能股票分析系统

> 基于 Spring Boot、Spring AI 与 Next.js 的股票分析系统。流水线 Agent 协作 + 可选的多空辩论仲裁，为单只股票提供结构化分析结果；另提供按个人投资模型做确定性复核的独立页面。行情、新闻、财报与估值只接受带来源的真实数据或明确标记的最后成功缓存，不生成模拟数据。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-orange.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F.svg)
![Next.js](https://img.shields.io/badge/Next.js-16.2.4-blueviolet.svg)

---

## 项目简介

AlphaMind 由一组专职 Agent 共同完成股票分析：

- `MarketAgent`：组合新浪实时报价与东方财富前复权日 K 线，并计算均线
- `TechnicalAgent`：按技术规则 V1（`TechnicalScoring`）计算 MACD / RSI / KDJ / 布林带并给出 10–95 的技术分及分项
- `SentimentAgent`：行情代理信号（涨跌幅、PE、换手率、量能）加东方财富个股新闻标题关键词的确定性加权评分，缺失项按可用权重归一化
- `PortfolioAgent`：整合前置结果，按策略生成交易信号与示意交易计划
- `BullAgent` / `BearAgent` / `NeutralAgent`：基于共享证据从多头、空头、中立视角展开辩论
- `ArbitratorAgent`：按实际票数裁决，并保存完整票数

工作模式：

1. **流水线模式（PIPELINE）**：Market → Technical → Sentiment → Portfolio
2. **辩论模式（DEBATE）**：完整执行上述四个阶段后，再追加 Bull → Bear → Neutral → Arbitrator 第五阶段。两种模式共享前四个阶段，不是两条独立链路。

其他能力：

- SSE 实时推送分析进度与各阶段数据
- 个股最新资讯卡片（东方财富新闻，含来源与过期标记）
- **个人投资模型复核**（`/investment-model`）：13 个观察标的、原文三档价格、五道决策门、S0–S4 信号等级和组合风险预算；自动取数东方财富财报与估值（PE/PB 近 5 年分位、扣非利润、经营现金流等）供人工复核参考，结论仍由人工确认
- **技术分离线回测**：与线上同一评分实现、无前视回放，输出可复现的 Markdown 报告
- 每周推荐 3 支“低位 + 价值”兼顾的重点观察股票，可一键带入分析
- 行情、新闻、财报估值均为“新鲜缓存 → 真实数据源 → 带告警的过期缓存 → 明确失败”
- LLM 可选接入（多模型 + 熔断）；未配置 API Key 时自动降级为确定性模板输出
- 分析报告持久化（PostgreSQL jsonb + Flyway），历史详情可完整还原并导出 JSON / Markdown / PDF
- 会话记忆（Redis，不可用时自动降级本地内存）

---

## 当前状态

截至 2026-10-26：

- 后端 `mvn test` 159 个离线单元测试全部通过（无 Spring 上下文、无数据库、无网络）
- 技术分、舆情分、综合分与规则一致度口径统一，评分依据、分项和数据缺口随报告保存并在页面、导出中展示
- 个人投资模型 v1.1 已接入，自动财报与估值快照随复核报告冻结保存
- 技术规则 V1 已完成首次历史回测：72 只观察股约 5 年、样本外持有 10 日，**未见区分力**（≥70 分档超额收益不高于 <40 分档，IC 未达显著）。新报告的技术评分说明和每条交易信号理由都会如实注明这一结论；综合分权重与 70/50 阈值暂未调整
- 舆情分依赖新闻，无法做时间对齐的历史回测；综合信号整体未经验证，仅作规则参考

详细变更记录见 [`changelog.md`](./changelog.md)，设计说明见 [`docs/`](./docs)。

---

## 技术栈

### 后端

| 组件         | 版本 / 说明                                                    |
| ------------ | -------------------------------------------------------------- |
| Java         | 17                                                             |
| Spring Boot  | 3.4.1                                                          |
| Spring AI    | 1.0.0 GA                                                       |
| 通信         | REST + Server-Sent Events (WebFlux Flux)                       |
| 数据库       | PostgreSQL（jsonb 字段）+ Flyway 迁移                          |
| 缓存         | Redis（可选，不可用时降级本地内存）                            |
| LLM Provider | OpenAI / DeepSeek / Anthropic（`LlmManager` 多模型 + 熔断）    |
| 外部数据     | 新浪实时报价；东方财富前复权日 K、个股新闻、数据中心财报与估值 |

### 前端

| 组件        | 版本 / 说明                                      |
| ----------- | ------------------------------------------------ |
| Next.js     | 16.2.4（App Router）                             |
| React       | 19                                               |
| TypeScript  | 5                                                |
| 状态管理    | Zustand                                          |
| UI          | Tailwind CSS 4 + Radix primitives + lucide-react |
| HTTP 客户端 | Axios（REST） + EventSource（SSE）               |
| 图表        | ECharts                                          |

---

## 项目结构

```text
AlphaMind/
├── backend/
│   ├── src/main/java/com/alphamind/
│   │   ├── agent/          # BaseAgent + 8 个专职 Agent、AgentRouter
│   │   ├── backtest/       # 技术分离线回测（回放、统计、报告）
│   │   ├── config/         # Spring / AI / CORS / Converter 配置
│   │   ├── controller/     # 分析、聊天、股票、个人投资模型、管理接口
│   │   ├── fundamental/    # 财报与估值数据源、缓存与中性提示
│   │   ├── investment/     # 个人投资模型目录、决策门引擎、复核复用
│   │   ├── market/         # 行情 Provider 链与缓存降级
│   │   ├── model/          # DTO / 枚举 / 实体（含 jsonb Entity）
│   │   ├── news/           # 个股新闻数据源与缓存
│   │   ├── repository/     # JPA 仓库
│   │   ├── scoring/        # TechnicalScoring、规则一致度、辩论证据
│   │   ├── service/        # 编排器、分析/历史服务、记忆、LLM 管理、Prompt 版本
│   │   └── strategy/       # 策略配置与信号规划
│   ├── src/main/resources/
│   │   ├── application.yml / application-dev.yml
│   │   └── db/migration/   # Flyway 迁移脚本
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── app/            # 页面：/ chat/ history/ watchlist/ investment-model/
│   │   ├── api/            # Axios API 客户端
│   │   ├── components/     # UI 组件（analysis/ chart/ common/ agent/ …）
│   │   ├── hooks/          # useSSE、useAnalysisSession 等
│   │   ├── stores/         # Zustand 状态（analysis / chat / watchlist）
│   │   └── types/          # TypeScript 类型定义
│   ├── next.config.ts      # /api/:path* 代理到 BACKEND_API_ORIGIN
│   └── package.json
├── e2e/                    # Playwright 端到端测试
├── docs/                   # 设计说明、评分口径、回测报告（docs/回测/）
├── docker-compose.yml
└── README.md
```

---

## 快速开始

### 前置条件

- Node.js 18+、npm 9+
- JDK 17+、Maven 3.8+
- PostgreSQL（dev 环境需提前创建数据库 `alphamind_dev`）

> Redis 和 LLM API Key 均为可选。无 Key 时所有 Agent 走确定性模板，完整流程依然可用。

---

## 环境变量

可在项目根目录创建 `.env` 文件（参考 `.env.example`，后端通过 Spring 的 `${VAR:default}` 语法读取）：

```env
# LLM（至少填一个才能启用真实 AI 响应；推荐 DEEPSEEK_API_KEY）
DEEPSEEK_API_KEY=
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
OPENAI_BASE_URL=

# 数据库
DB_USERNAME=
DB_PASSWORD=

# Redis（可选，不填时自动降级本地内存）
REDIS_PASSWORD=

# CORS（默认 http://localhost:3000）
CORS_ALLOWED_ORIGINS=http://localhost:3000

# 行情：是否允许调用真实数据源（false 时只能读取仍在有效期内的缓存，不会生成模拟行情）
FETCH_REAL_DATA=true
MARKET_CACHE_FRESH_TTL_SECONDS=30
MARKET_CACHE_STALE_MAX_AGE_SECONDS=86400
MARKET_HISTORY_DAYS=120

# 前端 rewrite 目标（默认 http://localhost:8080）
BACKEND_API_ORIGIN=http://localhost:8080
```

---

## 本地运行

### 1. 启动后端（dev profile）

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

默认地址：`http://localhost:8080`

`dev` profile **不会关闭数据库**：它连接 PostgreSQL `localhost:5432/alphamind_dev`，`ddl-auto: validate` 并启用 Flyway，未建库会启动失败。Redis 不可用、无 LLM Key 时均自动降级。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`。浏览器请求 `/api/v1/*` 由 `next.config.ts` 转发到后端，无需处理跨域。

### 3. 生产构建

```bash
cd backend && mvn clean package
cd frontend && npm run build && npm run start
```

### 4. Docker Compose 一键部署

```bash
docker compose up -d --build
```

服务端口：前端 `3000`、后端 `8080`、PostgreSQL `5432`、Redis `6379`。

- 先复制 `.env.example` 为 `.env`。
- 前端容器通过 `BACKEND_API_ORIGIN=http://backend:8080` 访问后端。

---

## Agent 工作流

```text
MarketAgent（实时报价 + 日 K + 均线）
  -> TechnicalAgent（技术规则 V1 技术分）
  -> SentimentAgent（行情代理信号 + 新闻标题关键词）
  -> PortfolioAgent（交易信号 + 示意交易计划）
  -> [仅 DEBATE] Bull -> Bear -> Neutral -> Arbitrator（按票数裁决）
```

辩论阶段顺序执行：Agent 是单例，按线程保存请求上下文，每次请求结束都会 `clearContext()`。

### LLM 调用策略

所有 Agent 继承 `BaseAgent`，通过 `llmCall()` 调用模型：`LlmManager`（按注册顺序尝试各模型，每模型 2 次重试，连续 3 次失败熔断 30 秒）→ 单个 `ChatClient` → 返回 `null` 并回退到模板输出。LLM 不可用时不会抛错。各 Agent 的系统提示词可通过管理接口创建新版本或回滚。

---

## 评分与信号

- **技术分**：基础 50 分，按 MACD 柱、RSI12、KDJ J、布林位置加减，限幅 10–95；≥70 偏多、<40 偏空。至少需要 34 根有效日 K，否则停止评分。
- **舆情分**（0–1）：行情代理信号原始权重为涨跌幅 40%、PE 25%、换手率 20%、量能 15%，缺失项按可用权重归一化；有新闻时行情占 88%、新闻标题关键词占 12%。
- **综合分** = 技术分 × 0.5 + 舆情分 × 100 × 0.5；≥70 BUY，≥50 HOLD，否则 SELL。
- **规则一致度**：衡量技术与舆情方向一致程度及偏离中性强度，再受信号覆盖与数据新鲜度上限约束；它不是胜率。BUY 在一致度低于策略门槛时降为 HOLD。
- **验证状态**：技术分回测未见区分力，舆情分无法回测；信号仅作规则参考，不构成投资建议。详见 [`docs/技术评分与规则一致度.md`](./docs/技术评分与规则一致度.md) 与 [`docs/评分口径与数据缺口.md`](./docs/评分口径与数据缺口.md)。

### 策略类型

| 策略 | 枚举值         | 仓位 | 止损 | 持仓周期 | 一致度门槛 | 默认模式   |
| ---- | -------------- | ---- | ---- | -------- | ---------- | ---------- |
| 保守 | `CONSERVATIVE` | 30%  | -5%  | 45天     | 75%        | `DEBATE`   |
| 平衡 | `BALANCED`     | 50%  | -7%  | 30天     | 65%        | `DEBATE`   |
| 激进 | `AGGRESSIVE`   | 80%  | -10% | 15天     | 55%        | `PIPELINE` |

策略只影响执行门槛和风险参数，不改变证据数值；目标价与止损是策略示意参数，不是价格预测。

---

## API 概览

所有 REST 接口返回 `ApiResponse<T>` 包装（`code`、`message`、`data`）；SSE 接口直接输出具名事件帧。

### 分析接口

```http
GET  /api/v1/analysis/stream?stockCode=600519&strategy=balanced&mode=DEBATE&sessionId=
POST /api/v1/analysis/analyze          # { "stockCode": "600519", "strategy": "BALANCED", "mode": "PIPELINE" }
GET  /api/v1/analysis/history?stockCode=600519&limit=20
GET  /api/v1/analysis/{reportId}       # 完整报告
```

| 参数           | 说明                                                     |
| -------------- | -------------------------------------------------------- |
| `stockCode`    | 股票代码（必填）                                         |
| `strategy`     | `conservative` / `balanced` / `aggressive`（大小写均可） |
| `mode`         | `PIPELINE` / `DEBATE`，优先级最高                        |
| `enableDebate` | 旧参数，优先级低于 `mode`                                |
| `sessionId`    | 会话 ID，用于带入近期会话上下文                          |

模式解析优先级：`mode` > `enableDebate` > 策略默认模式。

SSE 事件序列（具名事件）：

```text
event: stage     data: {"stage":"MARKET","message":"正在采集行情数据..."}
event: data      data: {"agentType":"MARKET","data":{...}}
...
event: result    data: {"code":200,"message":"分析完成","data":{完整 AnalysisReportDTO}}
event: complete  data: {...}
event: error     data: {...}          # 失败时
```

报告先持久化，再发送 `result`，最后发送 `complete`；客户端不要在看到第一个“像是结束”的事件时就断开。

### 聊天接口

```http
POST   /api/v1/chat/session?stockCode=600519&stockName=贵州茅台
POST   /api/v1/chat/message?sessionId={id}&content=适合抄底吗？&agentType=PORTFOLIO
GET    /api/v1/chat/stream/{sessionId}?message=怎么看当前走势？&agentType=TECHNICAL
GET    /api/v1/chat/history/{sessionId}?limit=20
DELETE /api/v1/chat/session/{sessionId}
```

消息以 `@Market`、`@Technical`、`@Bull` 等开头（大小写不敏感，`Agent` 后缀可省略）时路由到对应 Agent；无法识别时使用 `agentType`，默认 `PORTFOLIO`。

### 股票接口

```http
GET    /api/v1/stocks/search?query=600519          # 参数名是 query，不是 keyword
GET    /api/v1/stocks/{code}
GET    /api/v1/stocks/{code}/news                  # 个股最新资讯；数据源与缓存都不可用时 503
GET    /api/v1/stocks/recommendations/weekly       # 每周 3 支低位价值股（内置股票池启发式筛选）
GET    /api/v1/stocks/watchlist?userId=default
POST   /api/v1/stocks/watchlist/{code}?userId=default
DELETE /api/v1/stocks/watchlist/{code}?userId=default
GET    /api/v1/stocks/watchlist/{code}/check?userId=default
```

### 个人投资模型接口

```http
GET  /api/v1/investment-model                              # 模型定义、版本、原文校验和、13 个标的
POST /api/v1/investment-model/analyze                      # { stockCode, review? }：真实行情 + 确定性复核并保存报告
GET  /api/v1/investment-model/reviews/{stockCode}/latest   # 最近一次有来源复核的安全草稿
GET  /api/v1/investment-model/fundamentals/{stockCode}     # 财报/估值快照；模型外标的 400，不可用 503
```

### 管理接口

```http
GET  /api/v1/admin/prompts
GET  /api/v1/admin/prompts/{agentType}
GET  /api/v1/admin/prompts/{agentType}/versions
POST /api/v1/admin/prompts/{agentType}
POST /api/v1/admin/prompts/{agentType}/rollback/{version}
GET  /api/v1/admin/llm/health
POST /api/v1/admin/llm/{modelName}/reset
```

Actuator 仅暴露 `/actuator/health`、`/info`、`/metrics`。

---

## 前端页面

| 路由                | 功能                                                                                                                                    |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `/`                 | 主分析页：每周低位价值股、股票搜索、个股资讯、策略/模式选择、SSE 实时进度、行情、K 线、技术指标与评分依据、舆情分析、交易信号、辩论结果 |
| `/investment-model` | 个人投资模型复核：标的三档价格、自动财报与估值参考、人工证据表单、载入上次复核、复核结果与导出                                          |
| `/chat`             | 对话页：选择 Agent 或 @ 提及，流式聊天                                                                                                  |
| `/history`          | 历史记录：按股票筛选、完整报告详情、三方依据展开、JSON / Markdown / PDF 导出                                                            |
| `/watchlist`        | 自选股管理                                                                                                                              |

---

## 技术分回测

手动运行，不属于 `mvn test`；在 `backend/` 目录执行：

```bash
mvn -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) com.alphamind.backtest.TechnicalBacktestMain
```

可选参数：`--refresh`（忽略本地缓存重新取数）、`--out=路径`、`--raw-dir=目录`（读取事先保存的东方财富日 K 原始响应，适用于无法直连数据源的环境）。K 线缓存在 `backend/backtest-data/`（不入库），报告写入 `docs/回测/技术分回测-<日期>.md`。方法：每日用最近 120 根日 K 评分，t+1 开盘买入、持有 5/10/20 个交易日、往返成本约 0.2%，按分数段统计超额收益与 IC，样本内外 70%/30% 切分，判定标准事先写定。

---

## 开发指引

### 新增 Agent

1. 继承 `BaseAgent`，实现 `analyze()`、`chat()`、`getSystemPrompt()`
2. 在 `AgentType` 中新增枚举值，并接入 `AgentRouter.getAgent()`（switch 穷举，漏接不会通过编译）
3. 接入 `PipelineOrchestrator` 或 `DebateOrchestrator`，执行前初始化上下文、结束后 `clearContext()`
4. 使用 `llmCall()` + 确定性模板回退，保证无 Key 也能运行

### 修改评分规则

技术评分只在 `TechnicalScoring.score()` 中实现，线上 `TechnicalAgent` 与回测共用；修改后重新运行回测并更新报告。新增策略参数放在 `StrategyProfile` 上，不要在调用处按 `StrategyType` 分支。

### 数据源

行情读取顺序严格为“新鲜缓存 → 真实 Provider 链 → 明确标注的过期缓存 → `MarketDataUnavailableException`”，不要插入随机或静态兜底数据。`FETCH_REAL_DATA=false` 会禁止外部行情读取。过期读取的告警需要传递到界面。

### 已知限制

- 未实现登录鉴权与用户隔离：自选股 `userId` 默认为 `default`，管理接口未加授权，不适合直接公网部署
- 股票搜索与每周推荐基于代码内置的静态股票池，未接入外部股票元数据库
- 分析链路的行情 Provider 不提供 PE、PB 和总市值，这些字段保持为空、不参与估值结论；财报与估值自动取数目前只用于个人投资模型页面
- 新浪、东方财富均为公网接口，不提供生产 SLA
- 技术分回测未见区分力，综合信号整体未经验证；回测存在幸存者偏差、样本有限等局限（见回测报告）
- Redis 不可用时聊天历史只保存在内存，重启后丢失

---

## 验证命令

```bash
# 后端单元测试（完全离线）
cd backend && mvn test

# 前端 lint 与构建（构建同时是类型检查）
cd frontend && npm run lint && npm run build

# 端到端测试（会自动启动前端；需先启动后端）
cd e2e && npx playwright test

# 接口冒烟
curl 'http://localhost:8080/api/v1/stocks/search?query=600519'
curl --max-time 10 'http://localhost:8080/api/v1/analysis/stream?stockCode=600519&strategy=balanced&mode=PIPELINE'
```

---

## License

MIT License
