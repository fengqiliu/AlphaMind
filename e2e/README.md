# E2E 测试

普通 E2E 用例会启动前端开发服务器，并对后端接口做 mock。真实前后端链路测试不拦截任何接口，需要先启动可连接 PostgreSQL 的 Spring Boot 后端，再执行：

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
