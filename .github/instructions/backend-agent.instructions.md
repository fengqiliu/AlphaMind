---
applyTo: "backend/src/main/java/com/alphamind/agent/**"
---

# Backend Agent Development

## Creating a new Agent

1. Extend `BaseAgent` and pass your `AgentType` to `super()`.
2. Override `analyze(AnalysisReportDTO report)` — the only required method.
3. Read shared state with `getContext("key")`, write with `setContext("key", value)`.
4. Call `llmCall(prompt)` for LLM output — **always** handle `null` return by falling back to a template string.
5. Register the bean as `@Service` or `@Component`.

## ThreadLocal lifecycle (critical)

`contextHolder` is a `ThreadLocal<Map<String,Object>>`. Because agents are singletons running in a thread pool:
- **Set** context at the start of each request (orchestrator does this).
- **Clear** context with `clearContext()` in a `finally` block after every request to prevent state leaking between threads.

## LLM fallback chain

```
LlmManager.call()  →  ChatClient.call()  →  templateOutput()
```
Never propagate an exception when the LLM is unavailable. Return a meaningful template string instead.

## Context keys (canonical)

| Key | Type | Set by |
|-----|------|--------|
| `stockCode` | String | AnalysisController |
| `stockName` | String | AnalysisController |
| `strategy` | StrategyType | AnalysisController |
| `marketData` | MarketData | MarketAgent |
| `technicalIndicators` | TechnicalIndicators | TechnicalAgent |
| `sentimentData` | SentimentData | SentimentAgent |
| `tradeSignal` | TradeSignalDTO | PortfolioAgent |
| `confidence` | Double | PortfolioAgent |
| `sessionId` | String | AnalysisController |
| `contextSummary` | String | MemoryService |

## Pipeline execution order

`MarketAgent` → `TechnicalAgent` → `SentimentAgent` → `PortfolioAgent`

In debate mode, after pipeline completes: `BullAgent` / `BearAgent` / `NeutralAgent` (parallel) → `ArbitratorAgent`.

## AgentRouter

`AgentRouter` maps `AgentType` enums to agent beans. When adding a new agent, register its `AgentType` there.
