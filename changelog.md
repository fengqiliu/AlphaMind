# Changelog

本文件记录项目所有值得关注的变更，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added

- 个人模型支持载入上一份有来源复核，复用财报和文字证据、保留原有效期，并重置估值/逻辑/择时及当前组合风险
- 新报告保存并验证来源报告 ID，历史详情及导出支持回溯；新增 V4 部分索引与复用查询接口
- 接入用户个人投资模型 v1.1：13 观察标的、原文三档、五道决策门、S0–S4、专项否决和组合风险预算
- 新增个人模型复核页面、人工证据/日期/风险表单、真实 RSI14/ATR20、V3 JSONB 报告快照及历史导出
- 报告补齐实际策略、模式、三方辩论观点、置信度说明与独立最终信号的持久化；新增 V2 增量迁移
- 新增 `GET /api/v1/analysis/{reportId}` 完整报告读取，支持历史详情刷新与更早报告的直接链接
- 历史记录增加键盘可操作列表、股票筛选、完整分析详情、三方依据展开和导出；JSON/Markdown 保留报告执行配置
- 新增 `AnalysisService` 与 `AnalysisHistoryService`，集中分析用例、报告提交事务与数据库分页查询
- 新增 `useAnalysisSession`、`useWeeklyPicks` 和首页设置/流程/行情/舆情模块，图表按需加载
- 新增分析保存失败、SSE 完成顺序、旧连接事件隔离、页面离开清理和多尺寸布局回归
- 新增可组合行情 Provider 接口，以及新浪实时报价、东方财富前复权日 K 实现
- 新增 Redis/本地两级行情快照缓存、缓存年龄追踪和过期缓存失败降级
- 新增离线响应解析、Provider 链、缓存降级与 JSON 快照往返测试
- 新增 `com.alphamind.news` 真实新闻模块：东方财富个股新闻 Provider、Redis/本地两级新闻缓存与过期降级
- `SentimentDataDTO` 增加最新新闻标题、新闻来源与过期标记，舆情摘要与 LLM Prompt 基于真实新闻标题
- 辩论模式四个 Agent（Bull/Bear/Neutral/Arbitrator）的 LLM Prompt 注入真实新闻标题，模板论点引用真实新闻收录数
- 新增 `HeadlineSentimentScorer`：基于真实新闻标题关键词的确定性情绪信号，以 12% 权重混合进舆情评分；命中的关键词作为利好/利空因素透出，无新闻时评分退回纯行情信号
- 报告导出（Markdown/PDF）包含最新资讯列表与来源/过期标记；自选股页展示行情来源与实时/过期徽章；舆情卡展示新闻信号

### Changed

- 研究工作台采用统一浅色界面、青绿色强调色与响应式导航；分析设置始终可见，完整结论优先展示
- 移除侧边栏静态指数、未经检测的在线状态和缺失置信度时的默认数值；每周观察支持重试并说明排序依据
- `MarketAgent` 不再维护静态股票行情表或生成随机 K 线，只消费真实 Provider 快照
- 行情 DTO 与前端增加数据来源、实时/缓存状态、过期标记和降级告警
- `SentimentAgent` 不再按市值估算各平台新闻数量；新闻源不可用时保持为空并继续基于行情信号分析

### Fixed

- 上海 ETF 5 开头代码使用上海市场数据，并保留三位小数报价；行情 Agent 与个人模型复用统一映射
- 个人模型缺证据、证据过期、组合超限或基本面否决时不升级候选；模型外标的不静默替换，固定目标价/止损及置信度保持未提供
- 修复报告重载丢失辩论观点、置信度说明以及策略/模式被默认值替代的问题；旧记录缺失配置明确标记为未记录
- 报告时间按 PostgreSQL 微秒精度生成，并在读取时还原应用时区，避免首次结果与历史结果时间漂移
- 报告未提交成功时返回明确错误，不再发送成功 `result`；历史查询在数据库层限制最多 50 条
- 区分 SSE 业务错误与传输错误，避免错误被覆盖、默认消息重复处理或旧标的事件污染当前分析
- 修正浏览器测试中的过期会话响应结构、模糊选择器与将有限 SSE 响应误认为长连接的问题
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
