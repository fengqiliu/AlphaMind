package com.alphamind.controller;

import com.alphamind.agent.*;
import com.alphamind.model.dto.*;
import com.alphamind.model.entity.ChatMessageEntity;
import com.alphamind.model.entity.ChatSessionEntity;
import com.alphamind.model.enums.AgentType;
import com.alphamind.repository.ChatMessageRepository;
import com.alphamind.repository.ChatSessionRepository;
import com.alphamind.service.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话控制器 - 处理AI对话请求
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentRouter agentRouter;

    // Agent注入（保留用于直接上下文初始化）
    private final MarketAgent marketAgent;
    private final TechnicalAgent technicalAgent;
    private final SentimentAgent sentimentAgent;
    private final PortfolioAgent portfolioAgent;
    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    // 内存缓存 session 上下文（减少 DB 查询频率）
    private final java.util.concurrent.ConcurrentHashMap<String, AgentContext> sessionContextCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    @Transactional
    public ApiResponse<Map<String, String>> createSession(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName) {

        String sessionId = UUID.randomUUID().toString();
        String finalStockName = stockName != null ? stockName : stockCode;

        // 持久化会话到 DB
        ChatSessionEntity sessionEntity = ChatSessionEntity.builder()
                .sessionId(sessionId)
                .stockCode(stockCode)
                .stockName(finalStockName)
                .build();
        chatSessionRepository.save(sessionEntity);

        // 缓存上下文
        AgentContext context = new AgentContext();
        context.setSessionId(sessionId);
        context.setStockCode(stockCode);
        context.setStockName(finalStockName);
        sessionContextCache.put(sessionId, context);

        log.info("创建新会话: sessionId={}, stockCode={}", sessionId, stockCode);

        return ApiResponse.success(Map.of("sessionId", sessionId));
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    @Transactional
    public ApiResponse<ChatMessage> sendMessage(
            @RequestParam String sessionId,
            @RequestParam @NotBlank String content,
            @RequestParam(required = false, defaultValue = "PORTFOLIO") AgentType agentType) {

        AgentContext context = getOrLoadContext(sessionId);
        if (context == null) {
            return ApiResponse.error(404, "会话不存在");
        }

        // 保存用户消息（Redis + DB）
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        memoryService.saveMessage(sessionId, userMessage);
        persistMessage(sessionId, userMessage, null);

        // 路由消息：优先解析 @mention，否则使用显式 agentType
        AgentRouter.RouteResult route = agentRouter.route(content, agentType);
        BaseAgent agent = route.getAgent();
        AgentType resolvedType = route.getAgentType();
        initializeAgentContext(agent, context);

        // Agent处理（使用去除 @mention 前缀后的实际内容）
        ChatMessage agentInput = ChatMessage.builder()
                .id(userMessage.getId())
                .role(userMessage.getRole())
                .content(route.getContent())
                .timestamp(userMessage.getTimestamp())
                .build();
        ChatMessage response = agent.chat(agentInput);
        memoryService.saveMessage(sessionId, response);
        persistMessage(sessionId, response, resolvedType.name());

        // 更新会话活跃时间
        chatSessionRepository.touchSession(sessionId, OffsetDateTime.now());

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

        AgentContext context = getOrLoadContext(sessionId);
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
        persistMessage(sessionId, userMessage, null);

        return Flux.create(emitter -> {
            try {
                // 发送思考中状态
                emitter.next("data: {\"event\":\"thinking\",\"message\":\"Agent正在分析...\"}\n\n");

                // 路由消息：优先解析 @mention，否则使用显式 agentType
                AgentRouter.RouteResult route = agentRouter.route(message, agentType);
                BaseAgent agent = route.getAgent();
                AgentType resolvedType = route.getAgentType();
                initializeAgentContext(agent, context);

                // Agent处理（使用去除 @mention 前缀后的实际内容）
                ChatMessage agentInput = ChatMessage.builder()
                        .id(userMessage.getId())
                        .role(userMessage.getRole())
                        .content(route.getContent())
                        .timestamp(userMessage.getTimestamp())
                        .build();
                ChatMessage response = agent.chat(agentInput);
                memoryService.saveMessage(sessionId, response);
                persistMessage(sessionId, response, resolvedType.name());
                chatSessionRepository.touchSession(sessionId, OffsetDateTime.now());

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
    @Transactional
    public ApiResponse<Void> clearSession(@PathVariable String sessionId) {
        memoryService.clearSession(sessionId);
        sessionContextCache.remove(sessionId);
        chatSessionRepository.deleteById(sessionId);
        log.info("清除会话: sessionId={}", sessionId);
        return ApiResponse.success(null);
    }

    /** 从缓存或 DB 加载会话上下文 */
    private AgentContext getOrLoadContext(String sessionId) {
        return sessionContextCache.computeIfAbsent(sessionId, id ->
                chatSessionRepository.findById(id).map(entity -> {
                    AgentContext ctx = new AgentContext();
                    ctx.setSessionId(entity.getSessionId());
                    ctx.setStockCode(entity.getStockCode());
                    ctx.setStockName(entity.getStockName());
                    return ctx;
                }).orElse(null)
        );
    }

    /** 持久化消息到 DB（非关键路径，失败不影响主流程）*/
    private void persistMessage(String sessionId, ChatMessage msg, String agentTypeName) {
        try {
            chatSessionRepository.findById(sessionId).ifPresent(session -> {
                ChatMessageEntity entity = ChatMessageEntity.builder()
                        .id(msg.getId())
                        .session(session)
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .agentType(agentTypeName)
                        .agentName(msg.getAgentName())
                        .createdAt(msg.getTimestamp() != null
                                ? msg.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                                : OffsetDateTime.now())
                        .build();
                chatMessageRepository.save(entity);
            });
        } catch (Exception e) {
            log.warn("持久化消息失败（非致命）: sessionId={}, msgId={}", sessionId, msg.getId(), e);
        }
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
