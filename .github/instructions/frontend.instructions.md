---
applyTo: "frontend/src/**"
---

# Frontend Development (Next.js 16 App Router)

> **Before writing any frontend code**, check `frontend/AGENTS.md` — Next.js 16 has breaking changes from earlier versions. Read the relevant guide in `node_modules/next/dist/docs/`.

## URL conventions

- **Always use relative paths**: `/api/v1/...` — never hard-code `http://localhost:8080`.
- `next.config.ts` rewrites `/api/:path*` → `http://localhost:8080/api/:path*`.

## SSE (Server-Sent Events)

Use `EventSource`, not `fetch`, for streaming.

```ts
// Use the reusable hook
import { useSSE } from "@/hooks/useSSE";

// Always clean up in useEffect return
useEffect(() => {
  const source = new EventSource("/api/v1/analysis/stream?...");
  source.onmessage = (e) => { /* ... */ };
  return () => source.close(); // ← required to prevent leaks
}, []);
```

SSE event routing:
- `event: stage` → progress update (no data payload)
- `event: message` with `agentType` field → agent data
- `event: result` → final `ApiResponse<AnalysisReportDTO>` (parse `data` field)

## State management (Zustand)

| Store | File | Domain |
|-------|------|--------|
| `useAnalysisStore` | `src/stores/analysis.ts` | Pipeline/debate state, SSE events |
| `useChatStore` | `src/stores/chat.ts` | Chat sessions and messages |
| `useWatchlistStore` | `src/stores/watchlist.ts` | Watchlist CRUD |

Feed SSE events through `useAnalysisStore.handleSSEEvent()` rather than managing local state in components.

## API calls (non-streaming)

Use the Axios instance from `src/api/client.ts` — it already sets `baseURL: "/api/v1"`.

## Strategy values

Frontend sends lowercase (`conservative` / `balanced` / `aggressive`). Backend converts case-insensitively — do not change casing on the frontend.

## Stock search

Query param is `query` (not `keyword`): `GET /api/v1/stocks/search?query=...`

## No mocks

The app is wired to real APIs. Do not add mock data for analysis, chat, history, or watchlist flows.

## UI copy

All user-visible text and domain messages must be in **Chinese**.
