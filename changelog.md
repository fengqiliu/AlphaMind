# Changelog

本文件记录项目所有值得关注的变更，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

## [2026-05-05]

### Added

- 新增"每周低位价值股"能力：后端提供 `GET /api/v1/stocks/recommendations/weekly` 接口，固定返回 3 支本周重点观察股票
- 新增 `WeeklyStockRecommendation` DTO，包含排名、周标签、低位分、价值分、综合分、摘要和亮点信息
- 新增 `WeeklyValuePicks` 前端组件，展示本周精选股票卡片，支持查看推荐理由并一键设为当前分析标的
- 新增 `StockServiceTest` 单元测试，验证周荐逻辑返回恰好 3 支、排名连续、行业多样化

### Changed

- `StockService` 增加基于价格分位、行业折价、行业价值权重、板块稳定性和短期回撤的周荐评分逻辑
- 首页集成周荐区块，组件挂载时自动拉取数据，支持骨架屏加载态与错误提示
- `README.md` 补充每周推荐功能说明、接口文档与首页能力描述

## [2026-05-04]

### Security

- 修复后端 SSE/Chat 接口 JSON 注入漏洞：异常消息不再直接拼接进 JSON 字符串，改用 `ObjectMapper` 安全序列化（OWASP A03: Injection）

### Fixed

- `PipelineOrchestrator`：在 `finally` 块清理所有 Agent 的 ThreadLocal 上下文，防止线程池复用时数据泄漏
- `DebateOrchestrator`：同上，四个辩论 Agent（Bull / Bear / Neutral / Arbitrator）均在 `finally` 中调用 `clearContext()`
- `ChatController`：`sendMessage()` 与 `streamChat()` 均在 `finally` 块中清理 Agent ThreadLocal 上下文
- `StockSearch`（前端）：添加 `cancelled` 标志，搜索请求飞行中查询变更或组件卸载时不再执行过期的 `setState`
- `useSSE`（前端）：补全 `"result"` 事件类型，确保最终分析报告事件被 hook 正确捕获
