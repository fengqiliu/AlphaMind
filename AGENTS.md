# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

AlphaMind is a multi-agent intelligent stock analysis system using Spring AI and Next.js. It provides stock analysis through a pipeline of specialized agents (Market → Technical → Sentiment → Portfolio) and a debate mode where Bull/Bear/Neutral agents argue their positions before an Arbitrator makes a final decision.

## Build & Run Commands

### Frontend (Next.js 16)

```bash
cd frontend
npm install
npm run dev      # Development server on http://localhost:3000
npm run build    # Production build
npm run lint     # ESLint check
```

> There is no `typecheck` script and no frontend test runner; `npm run build` is the type-check gate.

### Backend (Spring Boot 3.5)

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run   # Runs on http://localhost:8080
mvn clean package     # Build JAR
mvn test              # Run tests
```

Single-test syntax:

```bash
mvn -Dtest=ClassName test
mvn -Dtest=ClassName#methodName test
```

Backend tests are plain JUnit 5 with no Spring context, no DB, and no network — `mvn test` runs fully offline.

Technical-score backtest (manual, never part of `mvn test`; run from `backend/`):

```bash
mvn -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) com.alphamind.backtest.TechnicalBacktestMain   # --refresh, --out=PATH, --raw-dir=DIR
```

It caches K-lines in `backend/backtest-data/` (gitignored) and writes `docs/回测/技术分回测-<date>.md`. `TechnicalScoring.score()` is the single scoring implementation shared by `TechnicalAgent` and the backtest — change scoring there, never in a copy.

### E2E Tests

```bash
cd e2e && npx playwright test
```

`playwright.config.ts` auto-starts the **frontend** dev server (`reuseExistingServer` when not CI), but not the backend — start the backend yourself for any test that hits `/api/v1`.

### Docker Compose

```bash
docker compose up -d --build
```

Services: frontend (3000), backend (8080), PostgreSQL (5432), Redis (6379)

### Environment Variables

LLM keys are optional — with no key, `llmCall()` returns null and agents fall back to deterministic template output; the full analysis flow still completes.

```bash
# LLM providers (all optional; DeepSeek recommended)
DEEPSEEK_API_KEY=<key>
OPENAI_API_KEY=<key>
OPENAI_BASE_URL=<url>
ANTHROPIC_API_KEY=<key>

# Infrastructure
DB_USERNAME=<user>       # default postgres
DB_PASSWORD=<password>
REDIS_PASSWORD=<password>
CORS_ALLOWED_ORIGINS=<origins>   # default http://localhost:3000

# Market data behavior (see application.yml for defaults)
FETCH_REAL_DATA=<bool>                    # false makes the provider chain throw instead of fetching
MARKET_CONNECT_TIMEOUT_MS=<n> / MARKET_READ_TIMEOUT_MS=<n>
MARKET_HISTORY_DAYS=<n>
MARKET_CACHE_FRESH_TTL_SECONDS=<n> / MARKET_CACHE_STALE_MAX_AGE_SECONDS=<n>

# Frontend
BACKEND_API_ORIGIN=<url>  # next.config.ts rewrite target, default http://localhost:8080
NEXT_PUBLIC_API_TOKEN=<token>  # build-time; must match ALPHAMIND_API_TOKEN when set
```

**Access token gate**: setting `ALPHAMIND_API_TOKEN` (Spring property `alphamind.api.token`) enables a shared-token auth filter on `/api/**`; unset (default) it is fully disabled and local dev/tests need nothing. Tokens are accepted as `Authorization: Bearer <t>`, `X-API-Token` header, or `?token=` query param (EventSource cannot set headers). OPTIONS preflights pass through. This is a single-user gate, not a multi-user account system — `userId` semantics elsewhere are unchanged. The frontend attaches it via `X-API-Token` on axios and `?token=` on SSE when `NEXT_PUBLIC_API_TOKEN` is set (build-time env).

## Architecture

### Strategy System

The strategy system uses a layered configuration with `StrategyType` enums (`CONSERVATIVE`/`BALANCED`/`AGGRESSIVE`) and `StrategyProfile` implementations:

- **Position ratios**: CONSERVATIVE=30%, BALANCED=50%, AGGRESSIVE=80%
- **Stop loss**: CONSERVATIVE=5%, BALANCED=7%, AGGRESSIVE=10%
- **Holding periods**: CONSERVATIVE=45 days, BALANCED=30 days, AGGRESSIVE=15 days
- **Default modes**: AGGRESSIVE→PIPELINE, others→DEBATE (via `StrategyProfile.getDefaultMode()`)

### AnalysisMode and Mode Resolution

`AnalysisMode` enum has two values: `PIPELINE` and `DEBATE`. Mode resolution follows this priority:

1. `mode` parameter if provided (e.g., `?mode=PIPELINE`)
2. `enableDebate` boolean (legacy compatibility): `true`→DEBATE, `false`→PIPELINE
3. Strategy default from `StrategyModeResolver.resolve(mode, enableDebate, strategy)`

The `StrategyModeResolver` (`backend/src/main/java/com/alphamind/strategy/`) handles the priority logic. AGGRESSIVE strategy defaults to PIPELINE; CONSERVATIVE and BALANCED default to DEBATE.

**DEBATE is pipeline *plus* a stage, not an alternative code path.** `PipelineOrchestrator.execute()` always runs stages 1–4; when the resolved mode is `DEBATE` it delegates stage 5 to `DebateOrchestrator.runDebate()` (Bull → Bear → Neutral → Arbitrator, sequential by design because agents are singletons with mutable per-thread context). Both orchestrators call `clearContext()` on every agent they touch in a `finally` block. Do not treat PIPELINE and DEBATE as two separate flows — they share stages 1–4 entirely.

### Strategy Signal Planning

`StrategySignalPlanner.plan()` turns scores into a `TradeSignalDTO`: composite = `technicalScore * 0.5 + sentimentScore * 50 * 0.5`; ≥70 BUY, ≥50 HOLD, else SELL. A BUY is downgraded to HOLD when confidence is below the profile's threshold. Keep new strategy parameters on `StrategyProfile` rather than branching on `StrategyType` at call sites.

### Scoring and Evidence Contracts

`AnalysisScoringPolicy` owns 0–100 equal-weight composite scores and `RULE_AGREEMENT_V1`. Rule agreement is not a return probability and must not increase with strategy position ratio. Preserve `confidence.method` through V5, including nested debate views; old reports keep a null method. Technical analysis requires 34 valid OHLC bars, never synthesized fallback bars. Save technical score contributions and sample size. Debate templates use `DebateEvidence`; single-point indicators do not establish crosses, trends, valuation or institutional views. Arbitration saves all six actual votes (three equal base votes, technical bucket two, sentiment bucket one). See `docs/技术评分与规则一致度.md`.

### Agent Context and Thread Safety

All agents share state via `ThreadLocal<Map<String,Object>>`. **Always call `clearContext()` after every request** to prevent thread-pool leaks. Common context keys: `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, `contextSummary`.

The `BaseAgent.llmCall()` follows a fallback chain: `LlmManager` (multi-model) → `ChatClient` (single) → `null`. Returning `null` is the contract for "fall back to a template" — never throw when the LLM is unavailable. If `contextSummary` is set, it is prepended to the user prompt as 【近期会话上下文】.

- `LlmManager` collects every `ChatModel` bean Spring AI auto-configures via `@Autowired(required = false)`, calls them in registration order with a per-model circuit breaker: 2 retries per call, 3 consecutive failures → OPEN, 30s cooldown → HALF_OPEN probe. Returns null only when every model is unavailable.
- `PromptManager` registers each agent's `getSystemPrompt()` as version 1 from `BaseAgent`'s `@PostConstruct`. `getEffectiveSystemPrompt()` prefers the managed active version — the hardcoded prompt in an agent class is a default, not necessarily what runs. Versions are managed at runtime through the Admin API.

**Market data read order is strict**: fresh cache → real provider chain → explicitly-labelled stale cache → `MarketDataUnavailableException`. Never insert a random or static fallback — callers must be able to distinguish real data from a degraded read. `MarketDataProviderChain` composes quote (Sina) and daily K-line (EastMoney) providers independently; a snapshot may mix sources and records each source name. `ResilientMarketDataService` returns a `MarketDataResolution` carrying `Status` (`FRESH_CACHE`/`LIVE`/`STALE_CACHE`), cache age, and a Chinese warning on stale reads — propagate that warning to the UI instead of swallowing it. Providers never supply PE/PB/market cap; those fields stay null and must not feed valuation conclusions.

**Concurrent IO (`ConcurrencyConfig.IoExecutor`)**: independent external reads run on one bounded pool (`alphamind.io.threads`, default 16): quote and K-lines in `MarketDataProviderChain`, article bodies in `StockNewsService` (cached by URL for 24h, failures not cached), and the news prefetch `PipelineOrchestrator` starts before stage 1 and hands to `SentimentAgent` via the `newsPrefetch` context key. Never run agent logic on this pool — agent context is `ThreadLocal`. The pool is deliberately registered under a non-`Executor` type: any `Executor` bean makes Spring Boot skip `applicationTaskExecutor`, which MVC async/SSE needs.

**Signal ledger (`ledger/`)**: `signal_outcomes` (Flyway V7) holds one row per report × horizon (5/10/20 trading days), written once when matured and never updated; report attributes (signal, confidence, strategy, mode, personal level) are frozen into the row. `OutcomeCalculator` uses the backtest convention — signal day = report date in Asia/Shanghai (non-trading days roll back), entry next trading day's open, exit close of bar t+h, `Costs.DEFAULT`, excess vs CSI 300 (`EastmoneyKlineMarketDataProvider.CSI300_SECID`; index codes must not go through the stock secid mapping). `SignalLedgerJob` runs by default at 16:10 Shanghai (`ALPHAMIND_LEDGER_CRON`, `-` disables) and shares a lock with `POST /api/v1/signals/evaluate`. The ledger only records; never feed it back into scoring automatically.

**Price alerts (`investment/alert/`)**: `PriceZones.classify` is the single band/zone rule shared by `PersonalModelEngine` (Gate 0) and alerts. `PriceAlertService` checks every catalog asset; `price_alert_state.deepest_alerted` (1–3 = tier, 4 = below third) makes an alert fire only when price reaches a deeper zone than already alerted, and resets to 0 when price returns above the first band. `STALE_CACHE` and fetch failures neither alert nor update state. `PriceAlertJob` runs every 15 minutes in trading hours (`ALPHAMIND_ALERTS_CRON`, `-` disables) and shares a lock with `POST /api/v1/investment-model/alerts/check`. Alerts only prompt a review — they never change gates or create BUY signals.

**Portfolio (`portfolio/`)**: `portfolio_profile` (Flyway V9) is a single user-maintained row (holdings jsonb, cash, low-volatility, peak); no broker sync, no trade log. `RiskBudgetCalculator` is pure: the planned buy comes out of cash + low-volatility so total value is unchanged and every post-trade weight shares one denominator; any market-priced holding without a current quote (stale cache or failure) withholds all percentages. `PortfolioService.riskBudget` raises the stored peak only from a complete computation. The client copies the result into `PersonalModelReview.portfolioBasis`; `PersonalModelEngine.portfolioNote` records the snapshot and flags edited values — the basis never changes gate outcomes, the form values do. `PortfolioSnapshotService`/`PortfolioSnapshotJob` write one `portfolio_snapshots` row per trading day (Flyway V10, weekdays 15:30 Asia/Shanghai, `ALPHAMIND_SNAPSHOT_CRON`, `-` disables): only a complete valuation is stored, a missing or stale quote leaves the day blank, same-day reruns overwrite one row. `PortfolioService.withSnapshotPeak` takes the larger of the manual peak and the snapshot maximum for drawdown (health and Gate 5) without rewriting the manual value; snapshots hold total value, not returns, so deposits/withdrawals distort the curve. `PortfolioHealthCalculator` (`GET /api/v1/portfolio/health`) is the sell-side review prompt: it only uses thresholds the model already has (position 3% growth / 5% other, model themes 20%, cash 15%, drawdown 20%, RSI14 > 70, missing/expired sourced review) — never invent exit rules, sell prices or trim sizes. User-defined themes, manual-value holdings and the gold-miner ETF (its limit is per-review manual input) skip limit checks; a holding without a current quote skips portfolio-level checks but per-holding RSI/review checks still run. It is computed on read and keeps no state. `REVIEW_EXPIRING` flags model holdings whose latest review expires within 7 days (the expiry day itself is still valid, same boundary as the engine); an expired review reports only `REVIEW_EXPIRED`.

**History archive (`archive/`)**: `news_archive` and `sentiment_archive` (Flyway V6) are append-only, so a later backtest can replay "what was visible at close on day t". `StockNewsService` archives every *live* news fetch (never cache reads) with the snapshot's real `retrievedAt`; `SentimentAgent` archives each sentiment computation with its inputs (change %, PE, turnover) and `ArchiveService.SENTIMENT_METHOD` — bump that constant whenever the sentiment scoring changes. Archiving is a side channel: failures are logged, never thrown. `NewsArchiveJob` walks the stock pool once per trigger and is off unless `ALPHAMIND_ARCHIVE_CRON` is set (cron `-` disables `@Scheduled`). Never update or delete archive rows. Backtests must filter by `fetched_at` / `computed_at`, not by publish time.

**Chat routing (`AgentRouter`)**: messages starting with `@Market`, `@TechnicalAgent`, `@Bull`, … (case-insensitive, `Agent` suffix optional) are routed to that agent with the mention stripped. Unrecognized mentions and plain messages fall back to the caller's `agentType`, defaulting to `PORTFOLIO`.

**Adding an agent**: ① extend `BaseAgent` (implement `analyze()`, `chat()`, `getSystemPrompt()`); ② add the `AgentType` value and wire it into `AgentRouter` (exhaustive switch — it will not compile otherwise); ③ hook it into `PipelineOrchestrator` or `DebateOrchestrator`, setting context before and clearing it after; ④ use `llmCall()` with a deterministic template fallback so the agent works without an API key.

### Backend (Spring Boot + Spring AI)

**Agent System** (`backend/src/main/java/com/alphamind/agent/`):

- `BaseAgent.java` - Abstract base class defining the agent contract
- `MarketAgent.java` - Fetches market/price data
- `TechnicalAgent.java` - Performs technical analysis (indicators, patterns)
- `SentimentAgent.java` - Analyzes news/sentiment data
- `PortfolioAgent.java` - Generates investment recommendations
- `BullAgent.java` / `BearAgent.java` - Debate mode advocates
- `NeutralAgent.java` - Debate mode neutral analyst
- `ArbitratorAgent.java` - Final decision maker in debate mode

**Key Services** (`backend/src/main/java/com/alphamind/service/`):

- `AnalysisService.java` - Shared synchronous/streaming use case; returns a report only after history commits
- `AnalysisHistoryService.java` - Report persistence transactions and bounded database history queries
- `PipelineOrchestrator.java` - Orchestrates the pipeline agent flow (Market → Technical → Sentiment → Portfolio)
- `MemoryService.java` - Manages conversation history via Redis
- `StockService.java` - Stock data operations

**API Controllers** (`backend/src/main/java/com/alphamind/controller/`):

- `AnalysisController.java` - SSE streaming analysis endpoint at `/api/v1/analysis/stream`
- `ChatController.java` - Chat session management at `/api/v1/chat/*`
- `StockController.java` - Stock search and watchlist at `/api/v1/stocks/*`

**DTOs** (`backend/src/main/java/com/alphamind/model/dto/`):

- `AnalysisReportDTO.java` - Final combined analysis report
- `TradeSignalDTO.java` - Buy/Sell/Hold recommendation with entry/exit prices
- `JudgmentDTO.java` - Debate arbitration result

Report `strategy` and `mode` contain actual execution enum names. Legacy reports without reliable metadata omit these fields. Preserve `debateViews`, confidence explanations and `finalSignal` on save/read/export; the final signal must not be overwritten by the trade plan's type. Add migrations after V1 rather than modifying applied migrations.

### Frontend (Next.js 16 + App Router)

**Routing** (`frontend/src/app/`):

- `page.tsx` - Main analysis page
- `chat/` - Chat interface
- `history/` - Analysis history
- `watchlist/` - Watchlist management
- `investment-model/` - Personal v1.1 decision gates, evidence review and risk budget; price-alert panel, portfolio health panel, "我的持仓" editor, portfolio-based Gate 5 fill
- `performance/` - Signal ledger performance by group

**Components** (`frontend/src/components/`):

- `agent/` - Agent message display and selection
- `analysis/` - Analysis and debate result display
- `chart/` - K-line and technical indicator charts (ECharts)
- `common/` - Shared UI components (Button, StockSearch, ConfidenceBar)
- `investment/` - Personal-model page sections (evidence, fundamentals, risk budget, `PriceAlertPanel`, `PortfolioEditor`)
- `ledger/` - `OutcomePanel` for history detail and shared formatting

**State Management** (`frontend/src/stores/`):

- Zustand stores for analysis state, chat sessions, and UI state
- `useAnalysisSession` owns analysis connection/cancellation/stock selection. Apply final reports atomically through `applyReport`; only a `result` event marks success. A `complete` event alone must not close the connection.
- `useSSE` filters stale connection events and distinguishes business `error` messages from transport errors. Do not add another EventSource owner in the analysis page.

**API Client** (`frontend/src/api/`):

- Axios-based API client for backend communication

### API Contract (Backend Port 8080)

**SSE Analysis Stream**:

```
GET /api/v1/analysis/stream?stockCode=600519&strategy=BALANCED&enableDebate=true
```

Events: `stage`, `data` (carries `agentType` + payload), `result` (an `ApiResponse<AnalysisReportDTO>`), `complete`, `error`. Order matters: the report is persisted, then `result`, then `complete` — clients must not disconnect on the first terminal-looking event.

Report lookup: `GET /api/v1/analysis/{reportId}`. History detail links use `/history?report={reportId}` and are independent of the recent-list limit. Also `POST /api/v1/analysis/analyze` (synchronous) and `GET /api/v1/analysis/history?stockCode=&limit=`.

Personal model: `GET /api/v1/investment-model`, `POST /api/v1/investment-model/analyze` with `{stockCode, review?}`. This dedicated deterministic use case does not call LLMs or trade APIs. Unknown/expired evidence never passes; do not use document holdings as current account data. Personal reports have nullable forecast/holding/confidence fields and render/export through `personalModel`, not the generic trade-plan cards. See `docs/个人投资模型接入说明.md` for source fidelity and conservative interpretations.

Fundamentals preview: `GET /api/v1/investment-model/fundamentals/{stockCode}` (400 outside the model, 503 when unavailable). Price alerts: `GET /api/v1/investment-model/alerts?includeAcknowledged=`, `GET /alerts/unread-count`, `POST /alerts/{id}/ack`, `POST /alerts/check` (409 while running). Portfolio: `GET /api/v1/portfolio` (404 when not entered), `PUT /api/v1/portfolio`, `POST /api/v1/portfolio/risk-budget` with `{stockCode, plannedBuyAmount}`, `GET /api/v1/portfolio/health` (404 when not entered), `GET /api/v1/portfolio/snapshots?days=` (default 180, clamped to 1–1000). Signal ledger: `GET /api/v1/signals/performance`, `GET /api/v1/signals/outcomes/{reportId}`, `POST /api/v1/signals/evaluate` (409 while running).

Review reuse: `GET /api/v1/investment-model/reviews/{stockCode}/latest` derives a draft from the latest sourced personal report. Preserve source dates, reset current valuation/logic/timing/leverage/portfolio decisions, and never mutate the source report. Validate `review.sourceReportId` against stock and model version before fetching market data. V4 adds a matching partial lookup index.

**Chat Session**:

```
POST /api/v1/chat/session?stockCode=600519
POST /api/v1/chat/message?sessionId={id}&content={msg}&agentType=PORTFOLIO
GET  /api/v1/chat/stream/{sessionId}?message={msg}&agentType=PORTFOLIO
GET  /api/v1/chat/history/{sessionId}
DELETE /api/v1/chat/session/{sessionId}
```

**Stocks**: `GET /api/v1/stocks/search?query=`, `GET /api/v1/stocks/{code}`, `GET /api/v1/stocks/{code}/news` (real headlines with source/stale flags; 503 when news source and cache are unavailable), `GET /api/v1/stocks/recommendations/weekly`, and `/api/v1/stocks/watchlist` CRUD.

**Admin**: `GET/POST /api/v1/admin/prompts/{agentType}`, `GET /api/v1/admin/prompts/{agentType}/versions`, `POST /api/v1/admin/prompts/{agentType}/rollback/{version}`, `GET /api/v1/admin/llm/health`, `POST /api/v1/admin/llm/{modelName}/reset`.

**Actuator**: `/actuator/health`, `/info`, `/metrics` only.

Analysis reports persist through `AnalysisReportRepository` (JPA, jsonb columns mapped by `AnalysisReportMapper`) — not an in-memory buffer. Chat sessions/messages have JPA entities plus Redis-backed `MemoryService` storage; watchlist is JPA-backed with `userId` defaulting to `"default"`. Stock metadata comes from `StockCatalog` (static in-code catalog); prices always come from the market provider chain.

**Strategy Types**: CONSERVATIVE (30% position, -5% stop), BALANCED (50%, -7%), AGGRESSIVE (80%, -10%)

## Key Conventions

- All REST endpoints return `ApiResponse<T>` envelope with `code`, `message`, and `data`; SSE endpoints are the exception, writing raw `event: <name>\ndata: <json>\n\n` frames
- Frontend strategy values are lowercase (`conservative`/`balanced`/`aggressive`); backend `StrategyTypeConverter` accepts case-insensitive input
- Stock search uses query parameter `query`, not `keyword`; watchlist defaults `userId` to `default`
- Frontend code should use relative `/api/v1/...` URLs; `frontend/next.config.ts` rewrites these to `BACKEND_API_ORIGIN` (default `http://localhost:8080`) — never hardcode the backend origin
- ECharts is imported on demand through `src/utils/echarts.ts` (only the registered series/components are bundled); never `import * as echarts from "echarts"` — register new chart types there
- Stack is Tailwind v4 + Radix primitives + lucide-react + ECharts + Zustand — not a component framework like Ant Design; SSE goes through `useSSE` (`EventSource`), and stores in `src/stores/` own their domain state and its SSE event handling
- No mock data flows — the app is wired to real APIs; do not reintroduce mocks for analysis, chat, history, or watchlist
- User-facing copy, prompts, and domain text are in Chinese — keep new UI strings consistent

## Important Notes

- Before modifying frontend code, read `frontend/AGENTS.md` and the relevant guides in `frontend/node_modules/next/dist/docs/` — Next.js 16 has breaking changes from earlier versions
- Backend has committed JUnit/Mockito tests; run `mvn test` for changes to backend behavior. A placeholder health test is not runtime health verification.
- Redis is optional; `MemoryService` falls back to local in-memory storage when Redis is unavailable
- Local backend development uses `dev` Spring profile and requires PostgreSQL database `alphamind_dev`; Flyway runs and Hibernate validates the schema.
- Analysis history is PostgreSQL-backed; queries are capped at 50 rows at the database level. Chat history uses Redis with in-memory fallback and database persistence.
- The frontend uses a shared light palette in `globals.css`; keep chart colors in sync with CSS variables. Never display invented live prices, connection health, or confidence values.
- Development scope and remaining priorities are tracked in `docs/开发计划.md`.
