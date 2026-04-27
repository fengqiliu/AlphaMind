# Copilot Instructions for AlphaMind

## Build, test, and lint commands

### Frontend (`frontend`)

- `npm install`
- `npm run dev`
- `npm run lint`
- `npm run build`

There is no frontend test runner configured in `frontend/package.json`.

### Backend (`backend`)

- `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`
- `mvn test`
- `mvn clean package`

Single-test syntax:

- `mvn -Dtest=ClassName test`
- `mvn -Dtest=ClassName#methodName test`

There are currently no committed backend test classes under `backend/src/test`, so `mvn test` is mainly a compile and Surefire baseline check.

## High-level architecture

AlphaMind is a two-part system: a Spring Boot 3.4 backend in `backend/` and a Next.js 16 App Router frontend in `frontend/`.

The main analysis flow is `AnalysisController -> PipelineOrchestrator -> MarketAgent -> TechnicalAgent -> SentimentAgent -> PortfolioAgent`. When debate mode is enabled, the pipeline keeps the same report context and lets `BullAgent`, `BearAgent`, and `NeutralAgent` feed `ArbitratorAgent` before returning the final `AnalysisReportDTO`.

Analysis streaming is server-sent events, not polling. `AnalysisController` emits incremental SSE updates during orchestration, then sends a final named `result` event whose payload is an `ApiResponse<AnalysisReportDTO>`. In the backend SSE model, progress events use `stage`, while data payload events use `agentType` plus `data`.

Chat is separate from the pipeline. `ChatController` stores per-session stock context in its in-memory `sessionContexts` map, routes each message to the selected agent, and asks `MemoryService` for recent context summaries. `MemoryService` prefers Redis for chat history, but falls back to local in-process memory when Redis is unavailable.

The frontend uses page-level App Router entries in `frontend/src/app/*`, Zustand feature stores in `frontend/src/stores/*`, Axios for non-streaming REST calls, and `EventSource` for streaming analysis/chat. Local browser requests stay relative to `/api/v1`; `frontend/next.config.ts` rewrites `/api/:path*` to `http://localhost:8080/api/:path*`, so frontend code should usually keep using relative `/api/v1/...` URLs instead of hard-coding the backend origin.

## Key conventions

- All agent implementations extend `BaseAgent` and pass shared state through `setContext()` / `getContext()`. Common keys include `stockCode`, `stockName`, `strategy`, `marketData`, `technicalIndicators`, `sentimentData`, `tradeSignal`, `confidence`, `sessionId`, and `contextSummary`.
- `BaseAgent.llmCall()` is optional by design. If no `ChatClient` is available, or the model call fails, agents are expected to fall back to template output instead of failing the whole request flow.
- REST endpoints consistently return the `ApiResponse<T>` envelope with `code`, `message`, and `data`.
- Frontend strategy values are lowercase strings (`conservative`, `balanced`, `aggressive`). Backend `StrategyTypeConverter` accepts case-insensitive input and maps it to the enum, so preserve the lowercase frontend values.
- The frontend is already wired to real backend APIs and SSE. Do not reintroduce mock analysis, chat, history, or watchlist flows when modifying the current app.
- Analysis history and chat history use different storage paths. `AnalysisController` keeps only the most recent 50 reports in an in-memory deque, while `MemoryService` stores chat history in Redis with an in-memory fallback.
- Stock search uses the query parameter name `query`, not `keyword`. Watchlist endpoints default `userId` to `default`.
- User-facing copy, prompts, and most inline domain text are written in Chinese. Keep new UI strings, agent prompts, and API-facing messages consistent with that tone unless a file already uses another language.
- If you modify the frontend, read `frontend/AGENTS.md` and `frontend/CLAUDE.md` first. This repo explicitly treats Next.js 16 behavior as a source of breaking changes that should be checked against current docs.
- Local backend development is expected to use the `dev` Spring profile. `application-dev.yml` disables MySQL/JPA auto-configuration, so backend tasks that do not need the production database should start with `SPRING_PROFILES_ACTIVE=dev`.
