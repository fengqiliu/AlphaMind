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

### Backend (Spring Boot 3.4)

```bash
cd backend
./mvnw spring-boot:run   # Runs on http://localhost:8080
./mvnw clean package     # Build JAR
./mvnw test              # Run tests
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

## Important Notes

- The frontend has its own `frontend/CLAUDE.md` with frontend-specific guidance
- Backend has no tests yet - test infrastructure not set up
- Redis is optional but required for conversation memory persistence
- The `frontend/AGENTS.md` contains important Next.js 16 breaking changes - read before modifying frontend code
