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

> There is no frontend test runner configured.

### Backend (Spring Boot 3.4)

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

### E2E Tests

```bash
cd e2e && npx playwright test
```

### Docker Compose

```bash
docker compose up -d --build
```

Services: frontend (3000), backend (8080), PostgreSQL (5432), Redis (6379)

### Environment Variables

```bash
# Required for LLM functionality
OPENAI_API_KEY=<key>
DEEPSEEK_API_KEY=<key>   # Recommended

# Optional
ANTHROPIC_API_KEY=<key>
DB_PASSWORD=<password>   # Only for production
REDIS_PASSWORD=<password>
```

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

### Scoring and Evidence Contracts

`AnalysisScoringPolicy` owns 0–100 equal-weight composite scores and `RULE_AGREEMENT_V1`. Rule agreement is not a return probability and must not increase with strategy position ratio. Preserve `confidence.method` through V5, including nested debate views; old reports keep a null method. Technical analysis requires 34 valid OHLC bars, never synthesized fallback bars. Save technical score contributions and sample size. Debate templates use `DebateEvidence`; single-point indicators do not establish crosses, trends, valuation or institutional views. Arbitration saves all six actual votes (three equal base votes, technical bucket two, sentiment bucket one). See `docs/技术评分与规则一致度.md`.

### Agent Context and Thread Safety

All agents share state via `ThreadLocal<Map<String,Object>>`. **Always call `clearContext()` after every request** to prevent thread-pool leaks. Common context keys: `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, `contextSummary`.

The `BaseAgent.llmCall()` follows a fallback chain: `LlmManager` (multi-model) → `ChatClient` (single) → template output. Never throw when LLM is unavailable.

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
- `investment-model/` - Personal v1.1 decision gates, evidence review and risk budget

**Components** (`frontend/src/components/`):

- `agent/` - Agent message display and selection
- `analysis/` - Analysis and debate result display
- `chart/` - K-line and technical indicator charts (ECharts)
- `common/` - Shared UI components (Button, StockSearch, ConfidenceBar)

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

Events: `stage` (MARKET/TECHNICAL/SENTIMENT/PORTFOLIO), `complete`, `result`

Report lookup: `GET /api/v1/analysis/{reportId}`. History detail links use `/history?report={reportId}` and are independent of the recent-list limit.

Personal model: `GET /api/v1/investment-model`, `POST /api/v1/investment-model/analyze` with `{stockCode, review?}`. This dedicated deterministic use case does not call LLMs or trade APIs. Unknown/expired evidence never passes; do not use document holdings as current account data. Personal reports have nullable forecast/holding/confidence fields and render/export through `personalModel`, not the generic trade-plan cards. See `docs/个人投资模型接入说明.md` for source fidelity and conservative interpretations.

Review reuse: `GET /api/v1/investment-model/reviews/{stockCode}/latest` derives a draft from the latest sourced personal report. Preserve source dates, reset current valuation/logic/timing/leverage/portfolio decisions, and never mutate the source report. Validate `review.sourceReportId` against stock and model version before fetching market data. V4 adds a matching partial lookup index.

**Chat Session**:

```
POST /api/v1/chat/session?stockCode=600519
POST /api/v1/chat/message?sessionId={id}&content={msg}&agentType=PORTFOLIO
GET  /api/v1/chat/stream/{sessionId}?message={msg}&agentType=PORTFOLIO
```

**Strategy Types**: CONSERVATIVE (30% position, -5% stop), BALANCED (50%, -7%), AGGRESSIVE (80%, -10%)

## Key Conventions

- All REST endpoints return `ApiResponse<T>` envelope with `code`, `message`, and `data`
- Frontend strategy values are lowercase (`conservative`/`balanced`/`aggressive`); backend `StrategyTypeConverter` accepts case-insensitive input
- Stock search uses query parameter `query`, not `keyword`; watchlist defaults `userId` to `default`
- Frontend code should use relative `/api/v1/...` URLs; `frontend/next.config.ts` rewrites these to `http://localhost:8080/api/:path*`
- User-facing copy, prompts, and domain text are in Chinese — keep new UI strings consistent

## Important Notes

- Before modifying frontend code, read `frontend/AGENTS.md` and the relevant guides in `frontend/node_modules/next/dist/docs/` — Next.js 16 has breaking changes from earlier versions
- Backend has committed JUnit/Mockito tests; run `mvn test` for changes to backend behavior. A placeholder health test is not runtime health verification.
- Redis is optional; `MemoryService` falls back to local in-memory storage when Redis is unavailable
- Local backend development uses `dev` Spring profile and requires PostgreSQL database `alphamind_dev`; Flyway runs and Hibernate validates the schema.
- Analysis history is PostgreSQL-backed; queries are capped at 50 rows at the database level. Chat history uses Redis with in-memory fallback and database persistence.
- The frontend uses a shared light palette in `globals.css`; keep chart colors in sync with CSS variables. Never display invented live prices, connection health, or confidence values.
- Development scope and remaining priorities are tracked in `docs/开发计划.md`.
