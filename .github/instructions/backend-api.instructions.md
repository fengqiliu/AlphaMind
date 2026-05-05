---
applyTo: "backend/src/main/java/com/alphamind/controller/**,backend/src/main/java/com/alphamind/model/**"
---

# Backend API & DTO Conventions

## Response envelope

All REST responses **must** use `ApiResponse<T>`:

```java
ApiResponse.success(data)      // code=200
ApiResponse.error("msg")       // code=5xx
```

Never return raw objects or `ResponseEntity` with unwrapped bodies.

## SSE endpoint pattern

- Produces `MediaType.TEXT_EVENT_STREAM_VALUE`
- Returns `Flux<String>` (Project Reactor)
- Progress events: `data: {"stage":"MARKET"}`
- Data events: `data: {"agentType":"MARKET","data":{...}}`
- Final event: `event: result\ndata: <ApiResponse<AnalysisReportDTO> JSON>`

## StrategyType conversion

`StrategyTypeConverter` handles case-insensitive binding for `@RequestParam StrategyType strategy`. Do not add manual `.toUpperCase()` calls.

## Mode resolution (AnalysisController)

Pass raw params to `StrategyModeResolver.resolve(mode, enableDebate, strategy)`. Never implement priority logic in controllers.

Priority: `mode` param > `enableDebate` > strategy default  
Defaults: AGGRESSIVE→PIPELINE, CONSERVATIVE/BALANCED→DEBATE

## Dev profile

`application-dev.yml` disables MySQL and JPA auto-configuration. Always run locally with `SPRING_PROFILES_ACTIVE=dev`.

## History storage

`AnalysisController` keeps an `ArrayDeque<AnalysisReportEntity>` capped at 50 entries. This is in-memory and resets on restart — do not treat it as durable persistence.
