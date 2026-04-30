# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

### Frontend (Next.js 16 + App Router)

**Routing** (`frontend/src/app/`):

- `page.tsx` - Main analysis page
- `chat/` - Chat interface
- `history/` - Analysis history
- `watchlist/` - Watchlist management

**Components** (`frontend/src/components/`):

- `agent/` - Agent message display and selection
- `analysis/` - Analysis and debate result display
- `chart/` - K-line and technical indicator charts (ECharts)
- `common/` - Shared UI components (Button, StockSearch, ConfidenceBar)

**State Management** (`frontend/src/stores/`):

- Zustand stores for analysis state, chat sessions, and UI state

**API Client** (`frontend/src/api/`):

- Axios-based API client for backend communication

### API Contract (Backend Port 8080)

**SSE Analysis Stream**:

```
GET /api/v1/analysis/stream?stockCode=600519&strategy=BALANCED&enableDebate=true
```

Events: `stage` (MARKET/TECHNICAL/SENTIMENT/PORTFOLIO), `complete`, `result`

**Chat Session**:

```
POST /api/v1/chat/session?stockCode=600519
POST /api/v1/chat/message?sessionId={id}&content={msg}&agentType=PORTFOLIO
GET  /api/v1/chat/stream/{sessionId}?message={msg}&agentType=PORTFOLIO
```

**Strategy Types**: CONSERVATIVE (30% position, -5% stop), BALANCED (50%, -7%), AGGRESSIVE (80%, -10%)

## Key Conventions

- All agents extend `BaseAgent` and share state via `setContext()`/`getContext()`. Common keys: `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, `contextSummary`
- `BaseAgent.llmCall()` is optional. If no `ChatClient` is available or the call fails, agents fall back to template output instead of failing the request
- All REST endpoints return `ApiResponse<T>` envelope with `code`, `message`, and `data`
- Frontend strategy values are lowercase (`conservative`/`balanced`/`aggressive`); backend `StrategyTypeConverter` accepts case-insensitive input
- Mode resolution: `mode` param > `enableDebate` > strategy default. AGGRESSIVE→PIPELINE, others→DEBATE
- Stock search uses query parameter `query`, not `keyword`; watchlist defaults `userId` to `default`
- Frontend code should use relative `/api/v1/...` URLs; `frontend/next.config.ts` rewrites these to `http://localhost:8080/api/:path*`
- User-facing copy, prompts, and domain text are in Chinese — keep new UI strings consistent

## Important Notes

- Before modifying frontend code, read `frontend/AGENTS.md` and `frontend/CLAUDE.md` — Next.js 16 has breaking changes from earlier versions
- Backend has test infrastructure configured but no committed test classes (`mvn test` is mainly a compile and Surefire baseline check)
- Redis is optional; `MemoryService` falls back to local in-memory storage when Redis is unavailable
- Local backend development uses `dev` Spring profile — `application-dev.yml` disables production DB auto-configuration
- Analysis history uses in-memory deque (most recent 50); chat history uses Redis with in-memory fallback
