# Changelog

本文件记录项目所有值得关注的变更，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added

- 新增可组合行情 Provider 接口，以及新浪实时报价、东方财富前复权日 K 实现
- 新增 Redis/本地两级行情快照缓存、缓存年龄追踪和过期缓存失败降级
- 新增离线响应解析、Provider 链、缓存降级与 JSON 快照往返测试
- 新增 `com.alphamind.news` 真实新闻模块：东方财富个股新闻 Provider、Redis/本地两级新闻缓存与过期降级
- `SentimentDataDTO` 增加最新新闻标题、新闻来源与过期标记，舆情摘要与 LLM Prompt 基于真实新闻标题
- 辩论模式四个 Agent（Bull/Bear/Neutral/Arbitrator）的 LLM Prompt 注入真实新闻标题，模板论点引用真实新闻收录数
- 新增 `HeadlineSentimentScorer`：基于真实新闻标题关键词的确定性情绪信号，以 12% 权重混合进舆情评分；命中的关键词作为利好/利空因素透出，无新闻时评分退回纯行情信号
- 报告导出（Markdown/PDF）包含最新资讯列表与来源/过期标记；自选股页展示行情来源与实时/过期徽章；舆情卡展示新闻信号

### Changed

- `MarketAgent` 不再维护静态股票行情表或生成随机 K 线，只消费真实 Provider 快照
- 行情 DTO 与前端增加数据来源、实时/缓存状态、过期标记和降级告警
- `SentimentAgent` 不再按市值估算各平台新闻数量；新闻源不可用时保持为空并继续基于行情信号分析

### Fixed

- 修正新浪实时报价成交量单位为股、东方财富日 K 成交量从手换算为股
- 未提供的 PE、PB、总市值保持为空，不再用零值伪装成估值结论
- 修复 `StockServiceTest` 使用过期 `StockService` 构造器导致 `mvn test` 编译失败的问题，并补充行情源不可用返回空列表的用例

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
