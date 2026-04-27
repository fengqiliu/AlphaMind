package com.alphamind.controller;

import com.alphamind.agent.*;
import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.service.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话控制器 - 处理AI对话请求
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    // Agent注入
    private final MarketAgent marketAgent;
    private final TechnicalAgent technicalAgent;
    private final SentimentAgent sentimentAgent;
    private final PortfolioAgent portfolioAgent;
    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    // 会话Agent上下文
    private final Map<String, AgentContext> sessionContexts = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public ApiResponse<Map<String, String>> createSession(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName) {

        String sessionId = UUID.randomUUID().toString();
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.setStockCode(stockCode);
        context.setStockName(stockName != null ? stockName : stockCode);

        sessionContexts.put(sessionId, context);

        log.info("创建新会话: sessionId={}, stockCode={}", sessionId, stockCode);

        return ApiResponse.success(Map.of("sessionId", sessionId));
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    public ApiResponse<ChatMessage> sendMessage(
            @RequestParam String sessionId,
            @RequestParam @NotBlank String content,
            @RequestParam(required = false, defaultValue = "PORTFOLIO") AgentType agentType) {

        AgentContext context = sessionContexts.get(sessionId);
        if (context == null) {
            return ApiResponse.error(404, "会话不存在");
        }

        // 保存用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        memoryService.saveMessage(sessionId, userMessage);

        // 获取对应Agent
        BaseAgent agent = getAgent(agentType);
        initializeAgentContext(agent, context);

        // Agent处理
        ChatMessage response = agent.chat(userMessage);
        memoryService.saveMessage(sessionId, response);

        log.info("会话消息: sessionId={}, agent={}", sessionId, agentType);

        return ApiResponse.success(response);
    }

    /**
     * SSE流式对话
     */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @PathVariable String sessionId,
            @RequestParam @NotBlank String message,
            @RequestParam(required = false, defaultValue = "PORTFOLIO") AgentType agentType) {

        AgentContext context = sessionContexts.get(sessionId);
        if (context == null) {
            return Flux.just("data: {\"error\": \"会话不存在\"}\n\n");
        }

        // 保存用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(message)
                .timestamp(LocalDateTime.now())
                .build();
        memoryService.saveMessage(sessionId, userMessage);

        return Flux.create(emitter -> {
            try {
                // 发送思考中状态
                emitter.next("data: {\"event\":\"thinking\",\"message\":\"Agent正在分析...\"}\n\n");

                // 获取对应Agent
                BaseAgent agent = getAgent(agentType);
                initializeAgentContext(agent, context);

                // Agent处理
                ChatMessage response = agent.chat(userMessage);
                memoryService.saveMessage(sessionId, response);

                // 发送响应
                String json = objectMapper.writeValueAsString(response);
                emitter.next("data: {\"event\":\"message\",\"data\":" + json + "}\n\n");

                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话失败: sessionId={}", sessionId, e);
                emitter.next("data: {\"event\":\"error\",\"message\":\"" + e.getMessage() + "\"}\n\n");
                emitter.complete();
            }
        });
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<ChatMessage>> getHistory(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<ChatMessage> history = memoryService.getSessionHistory(sessionId, limit);
        return ApiResponse.success(history);
    }

    /**
     * 清除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Void> clearSession(@PathVariable String sessionId) {
        memoryService.clearSession(sessionId);
        sessionContexts.remove(sessionId);
        log.info("清除会话: sessionId={}", sessionId);
        return ApiResponse.success(null);
    }

    private BaseAgent getAgent(AgentType type) {
        return switch (type) {
            case MARKET -> marketAgent;
            case TECHNICAL -> technicalAgent;
            case SENTIMENT -> sentimentAgent;
            case PORTFOLIO -> portfolioAgent;
            case BULL -> bullAgent;
            case BEAR -> bearAgent;
            case NEUTRAL -> neutralAgent;
            case ARBITRATOR -> arbitratorAgent;
        };
    }

    private void initializeAgentContext(BaseAgent agent, AgentContext context) {
        agent.clearContext();
        agent.setContext("sessionId", context.getSessionId());
        agent.setContext("stockCode", context.getStockCode());
        agent.setContext("stockName", context.getStockName());
        agent.setContext("contextSummary", memoryService.getContextSummary(context.getSessionId(), 10));
    }

    @lombok.Data
    private static class AgentContext {
        private String sessionId;
        private String stockCode;
        private String stockName;
    }
}
