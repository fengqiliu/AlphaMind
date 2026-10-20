# E2E 测试

普通 E2E 用例会启动前端开发服务器，并对后端接口做 mock。真实前后端链路测试不拦截任何接口，需要先启动可连接 PostgreSQL 的 Spring Boot 后端，再执行：

本机没有 Playwright 自带 Chromium 时，可用已安装的 Edge 运行：

```bash
PLAYWRIGHT_CHANNEL=msedge npx playwright test
```

`workspace.spec.ts` 检查 390 / 768 / 1440 像素布局，并将截图保存到 `output/playwright/`。

`history-detail.spec.ts` 使用测试数据验证历史详情、刷新恢复、键盘操作、JSON/Markdown 导出及手机布局。`real-report-history.integration.spec.ts` 在启用真实集成后验证 PIPELINE 与 DEBATE 报告的首次结果、数据库重载、历史接口和浏览器 JSON 下载完整一致。

`investment-model.spec.ts` 验证个人模型表单、13 标的、缺证据状态及手机布局；`real-investment-model.integration.spec.ts` 使用真实行情与数据库验证个人模型报告、刷新恢复及 JSON/Markdown/PDF 导出。新迁移 V3 必须已执行。

`review-reuse.spec.ts` 检查历史证据载入、日期保留、当前风险重置及读取失败不清空表单；`real-review-reuse.integration.spec.ts` 使用真实数据库验证最新有来源查询、跳过空复核/通用分析、来源报告不变和新报告回溯。需执行 V4 迁移。

旧库兼容性测试额外要求 `ALPHAMIND_E2E_LEGACY_FIXTURE=1`，仅用于已在 V1 schema 中准备 `legacy-report-v1` 样本、随后升级至 V2 的隔离测试环境；普通环境不需要设置。

```bash
# 终端 1：启动后端（需可连接 PostgreSQL，且能访问真实行情源）
cd backend
SPRING_PROFILES_ACTIVE=dev \\
FETCH_REAL_DATA=true \\
SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration,org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration \\
mvn -Dmaven.test.skip=true spring-boot:run

# 终端 2：启动已构建的前端，避免 dev server 的文件监听限制
cd frontend
npm run build
npm run start

# 终端 3：执行真实链路测试
cd e2e
ALPHAMIND_E2E_INTEGRATION=1 npx playwright test tests/real-analysis.integration.spec.ts
```

可用 `ALPHAMIND_BACKEND_ORIGIN` 覆盖健康检查地址，默认是 `http://localhost:8080`。测试会依次验证股票搜索、SSE `text/event-stream` 响应、完整报告字段和历史记录接口及页面。


### 评分依据与缺失数据回归

- `history-detail.spec.ts` 覆盖新评分依据、数据缺口、历史关注度估算标记和 JSON/Markdown/PDF 导出；保存桌面/手机截图与测试 PDF 到忽略的 `output/playwright/`。
- `real-report-history.integration.spec.ts` 在真实行情及 PostgreSQL 下核对缺失 PE 状态、无媒体关注度实测值，以及完整报告 JSONB 保存/重载/下载一致性。
- 评分算法说明见 `docs/评分口径与数据缺口.md`；真实 LLM 仍需单独验收。
