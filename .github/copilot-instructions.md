# Copilot Instructions for AlphaMind

> **Detailed docs**: [系统设计说明书](../docs/股票多Agent分析系统设计说明书.md) | [Frontend guide](../frontend/AGENTS.md) | [CLAUDE.md](../CLAUDE.md)

## Commands

| Area | Command |
|------|---------|
| Frontend dev | `cd frontend && npm run dev` |
| Frontend lint/build | `npm run lint` / `npm run build` |
| Backend dev | `cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` |
| Backend test | `mvn test` (compile + Surefire baseline; no committed test classes yet) |
| Single test | `mvn -Dtest=ClassName#methodName test` |
| E2E | `cd e2e && npx playwright test` |

## Architecture

Spring Boot 3.4 backend (`backend/`) + Next.js 16 App Router frontend (`frontend/`).

**Analysis pipeline**: `AnalysisController` → `PipelineOrchestrator` → `MarketAgent` → `TechnicalAgent` → `SentimentAgent` → `PortfolioAgent`  
**Debate mode**: same context, then `BullAgent` / `BearAgent` / `NeutralAgent` → `ArbitratorAgent` → `AnalysisReportDTO`

SSE stream endpoint: `GET /api/v1/analysis/stream` — progress events carry `stage`; agent data events carry `agentType` + `data`; final event is named `result` with `ApiResponse<AnalysisReportDTO>` payload.

## Critical conventions

### Backend
- **Agent pattern**: all agents extend `BaseAgent`. Shared state lives in a `ThreadLocal<Map<String,Object>>` — call `clearContext()` after every request to prevent thread-pool leaks. Common context keys: `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, `contextSummary`.
- **LLM fallback chain**: `LlmManager` (multi-model) → `ChatClient` (single) → template output. Never throw when LLM is unavailable; fall back gracefully.
- **Mode resolution priority**: `mode` param > `enableDebate` boolean > strategy default. AGGRESSIVE defaults to PIPELINE; CONSERVATIVE/BALANCED default to DEBATE. Logic lives in `StrategyModeResolver`.
- **API envelope**: all REST responses use `ApiResponse<T>` with `code`, `message`, `data`.
- **Dev profile**: `application-dev.yml` disables MySQL/JPA auto-config. Always start with `SPRING_PROFILES_ACTIVE=dev` locally.

### Frontend
- **No hard-coded backend URLs.** Always use relative `/api/v1/...`; `next.config.ts` rewrites to `http://localhost:8080`.
- **No mocks.** The app is wired to real APIs. Do not reintroduce mock flows for analysis, chat, history, or watchlist.
- **SSE via `EventSource`**, not `fetch`. Always close the `EventSource` in a cleanup function to avoid leaks.
- **Zustand stores** in `src/stores/` — `useAnalysisStore`, `useChatStore`, `useWatchlistStore`. Each store owns its domain state and exposes an `handleSSEEvent` or equivalent action.
- **Strategy values are lowercase** on the frontend (`conservative`, `balanced`, `aggressive`). Backend converter is case-insensitive.
- **Stock search param**: `query` (not `keyword`). Watchlist `userId` defaults to `"default"`.
- **Chinese copy**: all user-visible text, agent prompts, and domain messages must be in Chinese.
- **Next.js 16 breaking changes**: read `frontend/AGENTS.md` before any frontend work — it points to in-tree docs in `node_modules/next/dist/docs/`.

### Storage
- Analysis history: in-memory `ArrayDeque`, last 50 reports, reset on restart.
- Chat history: Redis → in-memory fallback (`MemoryService`).
- Watchlist: persisted via `StockController` / repository layer.

## Key files

| File | Purpose |
|------|---------|
| `backend/src/main/java/com/alphamind/agent/BaseAgent.java` | Agent contract + ThreadLocal context + LLM fallback chain |
| `backend/src/main/java/com/alphamind/service/PipelineOrchestrator.java` | Pipeline + debate orchestration |
| `backend/src/main/java/com/alphamind/controller/AnalysisController.java` | SSE streaming endpoint |
| `backend/src/main/java/com/alphamind/strategy/StrategyModeResolver.java` | Mode resolution priority logic |
| `backend/src/main/resources/application-dev.yml` | Dev profile — disables DB |
| `frontend/src/stores/analysis.ts` | Zustand analysis state + SSE event handler |
| `frontend/src/hooks/useSSE.ts` | Reusable EventSource hook |
| `frontend/src/api/client.ts` | Axios instance with base URL `/api/v1` |
| `frontend/next.config.ts` | API proxy rewrite rules |
| `frontend/src/types/index.ts` | Shared TypeScript types |
