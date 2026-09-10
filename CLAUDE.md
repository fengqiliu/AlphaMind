# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AlphaMind is a multi-agent stock analysis system (Spring Boot 3.4 + Spring AI backend, Next.js 16 frontend). A four-stage agent pipeline (Market → Technical → Sentiment → Portfolio) produces an `AnalysisReportDTO`; in DEBATE mode a fifth stage adds Bull/Bear/Neutral advocates and an Arbitrator. All domain text and user-facing copy is Chinese.

## Build & Run Commands

### Backend (Spring Boot 3.4, Java 17, Maven)

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run   # http://localhost:8080
mvn clean package
mvn test
```

Single test:

```bash
mvn -Dtest=StrategyModeResolverTest test
mvn -Dtest=StrategyModeResolverTest#shouldUseExplicitModeWhenProvided test
```

Backend tests are plain JUnit 5 with no Spring context, no DB, and no network — `mvn test` runs fully offline. Committed suites cover `strategy/`, `market/`, `service/`, and `agent/`.

### Frontend (Next.js 16.2, React 19, Tailwind v4)

```bash
cd frontend
npm install
npm run dev      # http://localhost:3000
npm run build
npm run lint     # bare `eslint`
```

There is no `typecheck` script and no frontend test runner; `npm run build` is the type-check gate.

### E2E (Playwright)

```bash
cd e2e && npx playwright test
```

`playwright.config.ts` auto-starts the **frontend** dev server (`reuseExistingServer` when not CI), but not the backend — start the backend yourself for any test that hits `/api/v1`.

### Docker Compose

```bash
docker compose up -d --build
```

Services: frontend (3000), backend (8080), PostgreSQL (5432), Redis (6379). Copy `.env.example` → `.env` first.

## Local Environment Requirements

**The `dev` profile does not disable the database.** `application-dev.yml` points at PostgreSQL `localhost:5432/alphamind_dev` with `ddl-auto: validate` and Flyway enabled — create that database before starting, or the app fails on boot. Schema lives in `backend/src/main/resources/db/migration/V1__init_schema.sql`; add migrations there rather than relying on Hibernate DDL.

Redis is genuinely optional: `MemoryService` catches every Redis exception and falls back to a `ConcurrentHashMap` (chat history is then lost on restart).

LLM keys are optional too — with no key, agents fall through to deterministic template output and the whole flow still completes. Recommended key: `DEEPSEEK_API_KEY`. Also read: `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `DB_USERNAME`/`DB_PASSWORD`, `REDIS_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `FETCH_REAL_DATA`, `MARKET_CACHE_FRESH_TTL_SECONDS`, `MARKET_CACHE_STALE_MAX_AGE_SECONDS`, `MARKET_HISTORY_DAYS`, and `BACKEND_API_ORIGIN` (frontend rewrite target).

## Architecture

### Orchestration: DEBATE is pipeline *plus* a stage, not an alternative

`PipelineOrchestrator.execute()` always runs stages 1–4. When the resolved mode is `DEBATE` it then delegates stage 5 to `DebateOrchestrator.runDebate()` (Bull → Bear → Neutral → Arbitrator, sequential by design because agents are singletons with mutable per-thread context). Both orchestrators call `clearContext()` on every agent they touch in a `finally` block.

Do not treat `PIPELINE` and `DEBATE` as two separate code paths — they share stages 1–4 entirely.

### Mode resolution

`StrategyModeResolver.resolve(mode, enableDebate, strategy)` applies priority: explicit `mode` → legacy `enableDebate` boolean → strategy default. `AnalysisController` has two `resolveMode` overloads (String and enum) because the SSE endpoint takes `mode` as a raw query string parsed via `AnalysisMode.fromValue`.

### Strategy layer (`strategy/`)

`StrategyType` enum + one `StrategyProfile` bean per type, looked up through `StrategyRegistry`:

| Strategy | Position | Stop loss | Holding | Default mode |
|---|---|---|---|---|
| CONSERVATIVE | 30% | 5% | 45d | DEBATE |
| BALANCED | 50% | 7% | 30d | DEBATE |
| AGGRESSIVE | 80% | 10% | 15d | PIPELINE |

`StrategySignalPlanner.plan()` turns scores into a `TradeSignalDTO`: composite = `technicalScore * 0.5 + sentimentScore * 50 * 0.5`; ≥70 BUY, ≥50 HOLD, else SELL. A BUY is downgraded to HOLD when confidence is below the profile's threshold. Keep new strategy parameters on `StrategyProfile` rather than branching on `StrategyType` at call sites.

### Agent contract and thread safety

Agents are singleton beans; per-request state lives in `BaseAgent`'s `ThreadLocal<Map<String,Object>>`. **Always `clearContext()` after a request** — the ThreadLocal leaks across pooled threads otherwise. Common keys: `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, `contextSummary`, plus `bullView`/`bearView`/`neutralView` in debate.

`BaseAgent.llmCall()` chain: `LlmManager` (multi-model + circuit breaker) → single `ChatClient` → `null`. Returning `null` is the contract for "fall back to a template" — never throw when the LLM is unavailable. If `contextSummary` is set, it is prepended to the user prompt as 【近期会话上下文】.

### LLM management (`LlmManager`)

Collects every `ChatModel` bean Spring AI auto-configures (OpenAI / DeepSeek / Anthropic) via `@Autowired(required = false)`, then calls them in registration order with a per-model circuit breaker: 2 retries per call, 3 consecutive failures → OPEN, 30s cooldown → HALF_OPEN probe. Returns `null` only when every model is unavailable.

### Prompt versioning (`PromptManager`)

Each agent registers `getSystemPrompt()` as version 1 from `BaseAgent`'s `@PostConstruct`. `getEffectiveSystemPrompt()` prefers the managed active version, so the hardcoded prompt in an agent class is a default, not necessarily what runs. Versions can be created and rolled back at runtime through `AdminController`.

### Market data (`market/`)

Read order is strict: **fresh cache → real provider chain → explicitly-labelled stale cache → `MarketDataUnavailableException`**. Never insert a random or static fallback — the whole package is built so callers can distinguish real data from a degraded read.

- `MarketDataProviderChain` composes providers independently for quote (`SinaQuoteMarketDataProvider`) and daily K-lines (`EastmoneyKlineMarketDataProvider`); a snapshot may legitimately mix sources, and each source name is recorded on `MarketDataSnapshot`. A history provider is skipped unless it returns at least `historyDays` bars.
- `ResilientMarketDataService` returns a `MarketDataResolution` carrying `Status` (`FRESH_CACHE` / `LIVE` / `STALE_CACHE`), age in seconds, and a Chinese warning string on stale reads. Propagate that warning to the UI rather than silently swallowing it.
- `FETCH_REAL_DATA=false` makes the chain throw immediately; only an in-age cached snapshot can satisfy the request.
- Providers do not supply PE, PB, or market cap — those fields stay null and must not feed valuation conclusions.

### Chat routing (`AgentRouter`)

Messages starting with `@Market`, `@TechnicalAgent`, `@Bull`, … (case-insensitive, `Agent` suffix optional) are routed to that agent with the mention stripped from the content. Unrecognized mentions and plain messages fall back to the caller's `agentType`, defaulting to `PORTFOLIO`.

### Persistence

Analysis reports are persisted through `AnalysisReportRepository` (JPA, jsonb columns mapped by `AnalysisReportMapper`) — not an in-memory buffer. `MAX_HISTORY = 50` in `AnalysisController` caps the *query* size. Chat sessions/messages have both JPA entities and Redis-backed `MemoryService` storage. Watchlist is JPA-backed with `userId` defaulting to `"default"`.

## API Contract (backend :8080)

All REST responses use the `ApiResponse<T>` envelope (`code`, `message`, `data`). SSE endpoints are the exception — they write raw `event: <name>\ndata: <json>\n\n` frames.

**Analysis** — `GET /api/v1/analysis/stream?stockCode=&stockName=&strategy=&mode=&enableDebate=&sessionId=`
Named events: `stage`, `data` (carries `agentType` + payload), `result` (an `ApiResponse<AnalysisReportDTO>`), `complete`, `error`. Order matters: the report is persisted, then `result`, then `complete` — clients must not disconnect on the first terminal-looking event.
Also `POST /api/v1/analysis/analyze` (synchronous) and `GET /api/v1/analysis/history?stockCode=&limit=`.

**Chat** — `POST /api/v1/chat/session`, `POST /api/v1/chat/message`, `GET /api/v1/chat/stream/{sessionId}`, `GET /api/v1/chat/history/{sessionId}`, `DELETE /api/v1/chat/session/{sessionId}`.

**Stocks** — `GET /api/v1/stocks/search?query=` (note: `query`, not `keyword`), `GET /api/v1/stocks/{code}`, `GET /api/v1/stocks/recommendations/weekly`, and `/api/v1/stocks/watchlist` CRUD.

**Admin** — `GET/POST /api/v1/admin/prompts/{agentType}`, `GET /api/v1/admin/prompts/{agentType}/versions`, `POST /api/v1/admin/prompts/{agentType}/rollback/{version}`, `GET /api/v1/admin/llm/health`, `POST /api/v1/admin/llm/{modelName}/reset`.

**Actuator** — `/actuator/health`, `/info`, `/metrics` only.

Stock search and weekly recommendations are served from a **static in-code `STOCK_DB` map** in `StockService`, not an external metadata source.

## Frontend Conventions

- Read `frontend/AGENTS.md` first: Next.js 16 has breaking changes from training data, and in-tree docs live in `frontend/node_modules/next/dist/docs/`.
- Stack is Tailwind v4 + Radix primitives + lucide-react + ECharts + Zustand — **not** a component framework like Ant Design.
- Use relative `/api/v1/...` URLs only. `next.config.ts` rewrites `/api/:path*` to `BACKEND_API_ORIGIN` (default `http://localhost:8080`). Never hardcode the backend origin.
- SSE goes through `useSSE` (`EventSource`, not `fetch`); it registers listeners for `stage`, `data`, `complete`, `error`, `message`, `result` and closes on error. Always close the connection in a cleanup function.
- Stores in `src/stores/` (`analysis`, `chat`, `watchlist`) own their domain state and the SSE event handling for it.
- Strategy values are lowercase on the wire (`conservative`/`balanced`/`aggressive`); backend `StrategyTypeConverter` is case-insensitive.
- No mock data flows — the app is wired to real APIs; do not reintroduce mocks for analysis, chat, history, or watchlist.
- New UI strings must be Chinese, matching existing copy.

## Adding an Agent

1. Extend `BaseAgent`, implement `analyze()`, `chat()`, `getSystemPrompt()`.
2. Add the value to `AgentType` and wire it into `AgentRouter.getAgent()` (the switch is exhaustive — it will not compile otherwise).
3. Hook it into `PipelineOrchestrator` or `DebateOrchestrator`, initializing context before and clearing it after.
4. Use `llmCall()` with a deterministic template fallback so the agent works without an API key.
