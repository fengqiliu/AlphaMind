package com.alphamind.service;

import com.alphamind.agent.AgentRouter;
import com.alphamind.agent.BaseAgent;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.entity.ChatMessageEntity;
import com.alphamind.model.entity.ChatSessionEntity;
import com.alphamind.model.enums.AgentType;
import com.alphamind.repository.AnalysisReportRepository;
import com.alphamind.repository.ChatMessageRepository;
import com.alphamind.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 对话用例：会话、消息持久化与 Agent 上下文装配。控制器只负责 HTTP / SSE 格式。
 *
 * <p>{@link #reply} 刻意不包在一个事务里：它要等 LLM 返回，且消息持久化失败不应影响回复；
 * 每次仓库写入各自提交（{@code touchSession} 自带事务）。
 */
@Slf4j
@Service
public class ChatService {

    /** 会话上下文只是减少 DB 查询的缓存，超出上限按最近最少使用淘汰，淘汰后从 DB 重新加载。 */
    static final int SESSION_CACHE_MAX = 1000;

    private final MemoryService memoryService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final AnalysisReportMapper analysisReportMapper;
    private final AgentRouter agentRouter;

    private final Map<String, SessionContext> sessionCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SessionContext> eldest) {
                    return size() > SESSION_CACHE_MAX;
                }
            });

    public ChatService(MemoryService memoryService,
                       ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository,
                       AnalysisReportRepository analysisReportRepository,
                       AnalysisReportMapper analysisReportMapper,
                       AgentRouter agentRouter) {
        this.memoryService = memoryService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.analysisReportMapper = analysisReportMapper;
        this.agentRouter = agentRouter;
    }

    /** 创建会话并返回会话 ID；未提供名称时以代码作为名称。 */
    public String createSession(String stockCode, String stockName) {
        String sessionId = UUID.randomUUID().toString();
        String finalStockName = stockName != null ? stockName : stockCode;
        chatSessionRepository.save(ChatSessionEntity.builder()
                .sessionId(sessionId)
                .stockCode(stockCode)
                .stockName(finalStockName)
                .build());
        sessionCache.put(sessionId, new SessionContext(sessionId, stockCode, finalStockName));
        log.info("创建新会话: sessionId={}, stockCode={}", sessionId, stockCode);
        return sessionId;
    }

    public boolean sessionExists(String sessionId) {
        return loadContext(sessionId).isPresent();
    }

    /**
     * 处理一条用户消息：保存用户消息 → 按 @mention 或 agentType 路由 → Agent 回复 → 保存回复。
     * 会话不存在时返回 empty；Agent 异常向上抛出，但无论成败都会清理 Agent 的 ThreadLocal 上下文。
     */
    public Optional<ChatMessage> reply(String sessionId, String content, AgentType agentType) {
        Optional<SessionContext> context = loadContext(sessionId);
        if (context.isEmpty()) {
            return Optional.empty();
        }

        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        memoryService.saveMessage(sessionId, userMessage);
        persistMessage(sessionId, userMessage, null);

        AgentRouter.RouteResult route = agentRouter.route(content, agentType);
        BaseAgent agent = route.getAgent();
        ChatMessage response;
        try {
            initializeAgentContext(agent, context.get());
            // 使用去除 @mention 前缀后的实际内容
            response = agent.chat(ChatMessage.builder()
                    .id(userMessage.getId())
                    .role(userMessage.getRole())
                    .content(route.getContent())
                    .timestamp(userMessage.getTimestamp())
                    .build());
        } finally {
            agent.clearContext();
        }

        memoryService.saveMessage(sessionId, response);
        persistMessage(sessionId, response, route.getAgentType().name());
        try {
            chatSessionRepository.touchSession(sessionId, OffsetDateTime.now());
        } catch (RuntimeException e) {
            log.warn("更新会话活跃时间失败（非致命）: sessionId={}", sessionId, e);
        }
        log.info("会话消息: sessionId={}, agent={}", sessionId, route.getAgentType());
        return Optional.of(response);
    }

    public List<ChatMessage> history(String sessionId, int limit) {
        return memoryService.getSessionHistory(sessionId, limit);
    }

    @Transactional
    public void clearSession(String sessionId) {
        memoryService.clearSession(sessionId);
        sessionCache.remove(sessionId);
        chatSessionRepository.deleteById(sessionId);
        log.info("清除会话: sessionId={}", sessionId);
    }

    int cachedSessionCount() {
        return sessionCache.size();
    }

    /** 从缓存或 DB 加载会话上下文。 */
    private Optional<SessionContext> loadContext(String sessionId) {
        SessionContext cached = sessionCache.get(sessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SessionContext> loaded = chatSessionRepository.findById(sessionId)
                .map(entity -> new SessionContext(entity.getSessionId(), entity.getStockCode(), entity.getStockName()));
        loaded.ifPresent(context -> sessionCache.put(sessionId, context));
        return loaded;
    }

    /** 持久化消息到 DB（非关键路径，失败不影响主流程）。 */
    private void persistMessage(String sessionId, ChatMessage message, String agentTypeName) {
        try {
            chatSessionRepository.findById(sessionId).ifPresent(session -> chatMessageRepository.save(
                    ChatMessageEntity.builder()
                            .id(message.getId())
                            .session(session)
                            .role(message.getRole())
                            .content(message.getContent())
                            .agentType(agentTypeName)
                            .agentName(message.getAgentName())
                            .createdAt(message.getTimestamp() != null
                                    ? message.getTimestamp().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                                    : OffsetDateTime.now())
                            .build()));
        } catch (RuntimeException e) {
            log.warn("持久化消息失败（非致命）: sessionId={}, msgId={}", sessionId, message.getId(), e);
        }
    }

    private void initializeAgentContext(BaseAgent agent, SessionContext context) {
        agent.clearContext();
        agent.setContext("sessionId", context.sessionId());
        setIfPresent(agent, "stockCode", context.stockCode());
        setIfPresent(agent, "stockName", context.stockName());

        String stockCode = context.stockCode();
        if (stockCode != null && !stockCode.isBlank()) {
            try {
                analysisReportRepository.findFirstByStockCodeOrderByCreatedAtDesc(stockCode)
                        .map(analysisReportMapper::toDTO)
                        .ifPresent(report -> {
                            setIfPresent(agent, "analysisReport", report);
                            setIfPresent(agent, "marketData", report.getMarketData());
                            setIfPresent(agent, "technicalIndicators", report.getTechnicalIndicators());
                            setIfPresent(agent, "sentimentData", report.getSentimentData());
                            setIfPresent(agent, "tradeSignal", report.getTradeSignal());
                            setIfPresent(agent, "confidence", report.getConfidence());
                            setIfPresent(agent, "judgment", report.getJudgment());
                        });
            } catch (RuntimeException e) {
                log.warn("加载最近分析报告失败，继续使用无报告上下文: stockCode={}", stockCode, e);
            }
        }
        agent.setContext("contextSummary", memoryService.getContextSummary(context.sessionId(), 10));
    }

    private static void setIfPresent(BaseAgent agent, String key, Object value) {
        if (value != null) {
            agent.setContext(key, value);
        }
    }

    private record SessionContext(String sessionId, String stockCode, String stockName) {}
}
