# AlphaMind - 多 Agent 智能股票分析系统

> 基于 Spring Boot、Spring AI 与 Next.js 的股票分析系统。流水线 Agent 协作 + 可选的多空辩论仲裁，为单只股票提供结构化分析结果；另提供按个人投资模型做确定性复核的独立页面。行情、新闻、财报与估值只接受带来源的真实数据或明确标记的最后成功缓存，不生成模拟数据。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-orange.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-6DB33F.svg)
![Next.js](https://img.shields.io/badge/Next.js-16.4.3-blueviolet.svg)

---

## 项目简介

AlphaMind 由一组专职 Agent 共同完成股票分析：

- `MarketAgent`：组合新浪实时报价与东方财富前复权日 K 线，并计算均线
- `TechnicalAgent`：按技术规则 V1（`TechnicalScoring`）计算 MACD / RSI / KDJ / 布林带并给出 10–95 的技术分及分项
- `SentimentAgent`：行情代理信号（涨跌幅、PE、换手率、量能）加东方财富个股新闻标题关键词的确定性加权评分，缺失项按可用权重归一化；LLM 舆情摘要附带前 3 篇新闻正文摘录（每篇最多 500 字，取不到时退回纯标题）
- `PortfolioAgent`：整合前置结果，按策略生成交易信号与示意交易计划
- `BullAgent` / `BearAgent` / `NeutralAgent`：基于共享证据从多头、空头、中立视角展开辩论
- `ArbitratorAgent`：按实际票数裁决，并保存完整票数

工作模式：

1. **流水线模式（PIPELINE）**：Market → Technical → Sentiment → Portfolio
2. **辩论模式（DEBATE）**：完整执行上述四个阶段后，再追加 Bull → Bear → Neutral → Arbitrator 第五阶段。两种模式共享前四个阶段，不是两条独立链路。

其他能力：

- SSE 实时推送分析进度与各阶段数据
- 个股最新资讯卡片（东方财富新闻，含来源与过期标记）
- 可选的共享访问令牌门禁：配置 `ALPHAMIND_API_TOKEN` 后所有 `/api/**` 需携带令牌，未配置时关闭
- **个人投资模型复核**（`/investment-model`）：13 个观察标的、原文三档价格、五道决策门、S0–S4 信号等级和组合风险预算；自动取数东方财富财报与估值（PE/PB 近 5 年分位、扣非利润、经营现金流等）供人工复核参考，结论仍由人工确认
- **技术分离线回测**：与线上同一评分实现、无前视回放，输出可复现的 Markdown 报告
- 每周推荐 3 支“低位 + 价值”兼顾的重点观察股票，可一键带入分析
- 行情、新闻、财报估值均为“新鲜缓存 → 真实数据源 → 带告警的过期缓存 → 明确失败”
- **信号账本与事后复盘**（`/performance`）：每份报告到期后自动记录 5/10/20 个交易日的实际收益与相对沪深 300 的超额收益，按信号、规则一致度、模式、策略、个人模型等级统计胜率；历史详情显示每份报告的“事后表现”
- **个人模型价格提醒**（`/investment-model`）：交易时段每 15 分钟检查 13 个观察标的，价格首次进入更深的档位（或跌破第三档）时生成站内提醒，回到首档之上后重新计数；只用实时行情，过期缓存不触发；提醒只提示去复核，不是买入信号
- **我的持仓与 Gate 5 推算**（`/investment-model`）：手动录入持仓、现金、低波动资产和组合历史高点（不对接券商）；复核时填入拟买入金额即可按实时价推算交易后单标的权重、主题合计权重、现金比例和组合回撤；任一持仓只有过期行情时不自动填写；报告冻结持仓快照并注明数值是否被手改
- **组合净值快照**：工作日收盘后（15:30）按当期行情记录一次组合总资产，页面显示净值与回撤曲线；缺行情的日子不记录、不补数；回撤高点取手填值与快照最大值中较大者。记录的是总资产而非收益率，追加或取出资金会改变曲线
- **持仓体检**：用实时行情检查已录入持仓是否超过单标的/主题控制线、现金是否低于底线、组合回撤是否越线、模型内持仓是否过热（RSI14 > 70）或复核证据过期；只提示复核，不给卖出价
- 历史存档：每次真实抓到新闻、每次舆情计算都只增不改地写入数据库（`news_archive`、`sentiment_archive`），可选定时任务对股票池每日抓取，为日后舆情分的时间对齐回测积累数据
- 外部数据并发获取：实时报价与日 K 同时请求，新闻正文并发抓取并按链接缓存 24 小时，分析开始即在后台预取新闻，与行情/技术阶段重叠
- LLM 可选接入（多模型 + 熔断）；未配置 API Key 时自动降级为确定性模板输出
- 分析报告持久化（PostgreSQL jsonb + Flyway），历史详情可完整还原并导出 JSON / Markdown / PDF
- 会话记忆（Redis，不可用时自动降级本地内存）

---

## 当前状态

截至 2026-11-06：

- 后端 `mvn test` 268 个离线单元测试全部通过（无 Spring 上下文、无数据库、无网络）
- 技术分、舆情分、综合分与规则一致度口径统一，评分依据、分项和数据缺口随报告保存并在页面、导出中展示
- 个人投资模型 v1.1 已接入，自动财报与估值快照随复核报告冻结保存；价格进档站内提醒；手动维护的持仓可推算 Gate 5 交易后权重
- 已有共享令牌门禁（单用户访问控制）；多用户账户与数据隔离尚未实现
- 依赖已升级到 Spring Boot 3.5.16 / Spring AI 1.1.8、Next.js 16.4.3 / React 19.3，前端生产依赖安全审计 0 漏洞
- 技术规则 V1 已完成首次历史回测：72 只观察股约 5 年、样本外持有 10 日，**未见区分力**（≥70 分档超额收益不高于 <40 分档，IC 未达显著）。新报告的技术评分说明和每条交易信号理由都会如实注明这一结论；综合分权重与 70/50 阈值暂未调整
- 舆情分依赖新闻，过去的数据无法做时间对齐回测；新闻与舆情输入已开始只增不改存档，积累数月后可回测；综合信号整体未经验证，仅作规则参考
- 信号账本已上线：报告到期后自动记录 5/10/20 日结果与相对沪深 300 超额，样本仍在积累

详细变更记录见 [`changelog.md`](./changelog.md)，设计说明见 [`docs/`](./docs)。

---

## 技术栈

### 后端

| 组件         | 版本 / 说明                                                    |
| ------------ | -------------------------------------------------------------- |
| Java         | 17                                                             |
| Spring Boot  | 3.5.16                                                         |
| Spring AI    | 1.1.8                                                          |
| 通信         | REST + Server-Sent Events (WebFlux Flux)                       |
| 数据库       | PostgreSQL（jsonb 字段）+ Flyway 迁移                          |
| 缓存         | Redis（可选，不可用时降级本地内存）                            |
| LLM Provider | OpenAI / DeepSeek / Anthropic（`LlmManager` 多模型 + 熔断）    |
| 外部数据     | 新浪实时报价；东方财富前复权日 K、个股新闻、数据中心财报与估值 |

### 前端

| 组件        | 版本 / 说明                                      |
| ----------- | ------------------------------------------------ |
| Next.js     | 16.4.3（App Router）                             |
| React       | 19.3                                             |
| TypeScript  | 5                                                |
| 状态管理    | Zustand                                          |
| UI          | Tailwind CSS 4 + Radix primitives + lucide-react |
| HTTP 客户端 | Axios（REST） + EventSource（SSE）               |
| 图表        | ECharts（按需引入）                              |

---

## 项目结构

```text
AlphaMind/
├── backend/
│   ├── src/main/java/com/alphamind/
│   │   ├── agent/          # BaseAgent + 8 个专职 Agent、AgentRouter
│   │   ├── archive/        # 新闻与舆情输入只增不改存档、定时抓取
│   │   ├── backtest/       # 技术分离线回测（回放、统计、报告）
│   │   ├── config/         # Spring / AI / CORS / Converter 配置
│   │   ├── controller/     # 分析、聊天、股票、个人投资模型、提醒、组合、信号账本、管理接口
│   │   ├── fundamental/    # 财报与估值数据源、缓存与中性提示
│   │   ├── investment/     # 个人投资模型目录、决策门引擎、档位判定、复核复用；alert/ 价格提醒
│   │   ├── ledger/         # 信号账本：到期结果计算、分组表现、定时补算
│   │   ├── market/         # 行情 Provider 链与缓存降级
│   │   ├── model/          # DTO / 枚举 / 实体（含 jsonb Entity）
│   │   ├── news/           # 个股新闻数据源与缓存
│   │   ├── portfolio/      # 手动维护的当前组合与 Gate 5 推算
│   │   ├── repository/     # JPA 仓库
│   │   ├── scoring/        # TechnicalScoring、规则一致度、辩论证据
│   │   ├── service/        # 编排器、分析/历史/对话服务、记忆、LLM 管理、Prompt 版本
│   │   └── strategy/       # 策略配置与信号规划
│   ├── src/main/resources/
│   │   ├── application.yml / application-dev.yml
│   │   └── db/migration/   # Flyway 迁移脚本
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── app/            # 页面：/ chat/ history/ watchlist/ investment-model/ performance/
│   │   ├── api/            # Axios API 客户端
│   │   ├── components/     # UI 组件（analysis/ chart/ common/ agent/ investment/ …）
│   │   ├── hooks/          # useSSE、useAnalysisSession、useFundamentals 等
│   │   ├── stores/         # Zustand 状态（analysis / chat / watchlist）
│   │   ├── types/          # TypeScript 类型定义
│   │   └── utils/          # 导出（export/：JSON / Markdown / PDF）、ECharts 按需注册等
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
# 外部数据并发请求的线程数（默认 16）
ALPHAMIND_IO_THREADS=16

# 信号账本补算（默认每个交易日 16:10，上海时间；设为 - 关闭）
ALPHAMIND_LEDGER_CRON=0 10 16 * * MON-FRI
# 个人模型价格提醒（默认交易时段每 15 分钟，上海时间；设为 - 关闭）
ALPHAMIND_ALERTS_CRON=0 */15 9-15 * * MON-FRI
# 组合净值每日快照（默认工作日 15:30，上海时间；设为 - 关闭）
ALPHAMIND_SNAPSHOT_CRON=0 30 15 * * MON-FRI

# 新闻存档定时任务（默认关闭）：Spring cron，Asia/Shanghai 时区，例如收盘后每个交易日一次
ALPHAMIND_ARCHIVE_CRON=

# 前端 rewrite 目标（默认 http://localhost:8080）
BACKEND_API_ORIGIN=http://localhost:8080

# 访问令牌（公网部署前强烈建议设置；留空表示不启用认证，仅限本地使用）
ALPHAMIND_API_TOKEN=
# 前端需设置为相同值；NEXT_PUBLIC_* 在构建时写入客户端代码
NEXT_PUBLIC_API_TOKEN=
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
- 启用令牌时同时设置 `ALPHAMIND_API_TOKEN` 与 `NEXT_PUBLIC_API_TOKEN`；后者是构建期变量，修改后需重新构建前端镜像。

---

## Agent 工作流

```text
MarketAgent（实时报价 + 日 K + 均线）
  -> TechnicalAgent（技术规则 V1 技术分）
  -> SentimentAgent（行情代理信号 + 新闻标题关键词；LLM 摘要附正文摘录）
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

**访问令牌**：配置 `ALPHAMIND_API_TOKEN` 后，`/api/**` 需通过 `Authorization: Bearer <token>`、`X-API-Token` 请求头或 `?token=` 查询参数携带令牌（`EventSource` 无法自定义请求头，SSE 只能用查询参数），否则返回 401。前端 axios 自动附加请求头，`useSSE` 自动附加查询参数。

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
GET  /api/v1/investment-model/alerts?includeAcknowledged=false  # 最近 50 条价格提醒（默认只含未读）
GET  /api/v1/investment-model/alerts/unread-count          # 未读提醒数
POST /api/v1/investment-model/alerts/{id}/ack              # 标为已读
POST /api/v1/investment-model/alerts/check                 # 立即检查档位；上一轮未结束 409
```

### 组合接口

```http
GET  /api/v1/portfolio                # 当前组合；未录入 404
PUT  /api/v1/portfolio                # 整体覆盖保存；无法估值或代码重复 400
POST /api/v1/portfolio/risk-budget    # { stockCode, plannedBuyAmount }：推算交易后 Gate 5 四项
GET  /api/v1/portfolio/health         # 持仓体检（越线、过热、证据过期）；未录入 404
GET  /api/v1/portfolio/snapshots      # 组合净值快照与回撤，?days=（默认 180，最大 1000）
```

### 信号账本接口

```http
GET  /api/v1/signals/performance           # 分组事后表现
GET  /api/v1/signals/outcomes/{reportId}   # 单份报告的 5/10/20 日结果
POST /api/v1/signals/evaluate              # 立即补算；上一轮未结束 409
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
| `/investment-model` | 个人投资模型复核：标的三档价格、自动财报与估值参考、人工证据表单、载入上次复核、价格提醒、持仓体检、净值与回撤曲线、我的持仓与 Gate 5 推算、复核结果与导出                                          |
| `/chat`             | 对话页：选择 Agent 或 @ 提及，流式聊天                                                                                                  |
| `/performance`      | 信号表现：各类信号事后的平均超额收益、胜率与样本数，样本不足时明确标注                                                                     |
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

架构、约定、命令与接口契约的唯一权威来源是 [`AGENTS.md`](./AGENTS.md)（`CLAUDE.md` 只是指向它的入口）；剩余优先事项见 [`docs/开发计划.md`](./docs/开发计划.md)。

### 新增 Agent

1. 继承 `BaseAgent`，实现 `analyze()`、`chat()`、`getSystemPrompt()`
2. 在 `AgentType` 中新增枚举值，并接入 `AgentRouter.getAgent()`（switch 穷举，漏接不会通过编译）
3. 接入 `PipelineOrchestrator` 或 `DebateOrchestrator`，执行前初始化上下文、结束后 `clearContext()`
4. 使用 `llmCall()` + 确定性模板回退，保证无 Key 也能运行

### 修改评分规则

技术评分只在 `TechnicalScoring.score()` 中实现，线上 `TechnicalAgent` 与回测共用；修改后重新运行回测并更新报告。新增策略参数放在 `StrategyProfile` 上，不要在调用处按 `StrategyType` 分支。

### 数据源

行情读取顺序严格为“新鲜缓存 → 真实 Provider 链 → 明确标注的过期缓存 → `MarketDataUnavailableException`”，不要插入随机或静态兜底数据。`FETCH_REAL_DATA=false` 会禁止外部行情读取。过期读取的告警需要传递到界面。

互不依赖的外部读取统一在 `ConcurrencyConfig.IoExecutor` 线程池上并发执行；不要在该线程池里运行 Agent 逻辑（Agent 上下文是 `ThreadLocal`）。

### 前端图表

ECharts 通过 `src/utils/echarts.ts` 按需注册，只打包用到的图表与组件；新增图表类型时在那里注册，不要 `import * as echarts from "echarts"`。

### 已知限制

- 只有单一共享令牌，没有多用户账户与数据隔离：自选股 `userId` 默认为 `default`，管理接口与普通接口使用同一令牌；令牌经由前端构建写入客户端代码，持有前端页面即可获得令牌，只适合个人或小范围使用
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
