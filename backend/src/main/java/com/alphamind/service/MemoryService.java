package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆服务 - 管理会话上下文和分析历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** LlmManager 可选注入（用于智能摘要，无 LLM 时降级为模板摘要） */
    @Autowired(required = false)
    @Lazy
    private LlmManager llmManager;

    private static final String SESSION_KEY_PREFIX   = "alphamind:session:";
    private static final String HISTORY_KEY_PREFIX   = "alphamind:history:";
    private static final String SUMMARY_KEY_PREFIX   = "alphamind:summary:";
    private static final Duration SESSION_TTL        = Duration.ofHours(24);
    private static final Duration SUMMARY_TTL        = Duration.ofMinutes(30);
    private static final int     SUMMARY_MAX_MESSAGES = 20;

    private final Map<String, List<ChatMessage>> localMemory = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────────
    // 会话消息存储
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 保存会话消息
     */
    public void saveMessage(String sessionId, ChatMessage message) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            redisTemplate.opsForList().rightPush(key, message);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            // Fallback to local memory
            localMemory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
            log.warn("Redis unavailable, using local memory for session: {}", sessionId);
        }
    }

    /**
     * 获取会话历史
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getSessionHistory(String sessionId, int limit) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            @SuppressWarnings("unchecked")
            List<ChatMessage> messages = (List<ChatMessage>) (List<?>) redisTemplate.opsForList().range(key, -limit, -1);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            // Fallback to local memory
            List<ChatMessage> local = localMemory.getOrDefault(sessionId, new ArrayList<>());
            int start = Math.max(0, local.size() - limit);
            return local.subList(start, local.size());
        }
    }

    /**
     * 保存分析报告到历史
     */
    public void saveAnalysisReport(AnalysisReportDTO report) {
        String key = HISTORY_KEY_PREFIX + report.getStockCode();
        try {
            redisTemplate.opsForList().rightPush(key, report);
            // Keep only last 20 reports per stock
            redisTemplate.opsForList().trim(key, -20, -1);
        } catch (Exception e) {
            log.warn("Failed to save analysis report to Redis: {}", e.getMessage());
        }
    }

    /**
     * 获取股票分析历史
     */
    @SuppressWarnings("unchecked")
    public List<AnalysisReportDTO> getStockHistory(String stockCode, int limit) {
        String key = HISTORY_KEY_PREFIX + stockCode;
        try {
            @SuppressWarnings("unchecked")
            List<AnalysisReportDTO> reports = (List<AnalysisReportDTO>) (List<?>) redisTemplate.opsForList().range(key, -limit, -1);
            return reports != null ? reports : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to get history from Redis: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 清除会话
     */
    public void clearSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            localMemory.remove(sessionId);
        }
    }

    /**
     * 获取最近的上下文摘要。
     *
     * <p>优先从 Redis 摘要缓存中读取；缓存未命中时：
     * <ol>
     *   <li>若 LlmManager 可用，则用 LLM 生成智能摘要并写入缓存</li>
     *   <li>否则降级为模板摘要</li>
     * </ol>
     *
     * @param sessionId   会话 ID
     * @param maxMessages 最多召回的消息数量
     * @return 摘要文本（空会话返回空字符串）
     */
    public String getContextSummary(String sessionId, int maxMessages) {
        List<ChatMessage> history = getSessionHistory(sessionId,
                Math.min(maxMessages, SUMMARY_MAX_MESSAGES));
        if (history.isEmpty()) return "";

        // 尝试从摘要缓存读取
        String cachedSummary = getSummaryCached(sessionId);
        if (cachedSummary != null) return cachedSummary;

        // 生成摘要
        String summary = (llmManager != null && llmManager.isAvailable())
                ? generateLlmSummary(history)
                : generateTemplateSummary(history);

        // 写入摘要缓存
        saveSummaryCached(sessionId, summary);
        return summary;
    }

    /**
     * 获取最近 N 条消息的股票上下文（用于 Agent 构建 Prompt）。
     *
     * @param sessionId    会话 ID
     * @param stockCode    当前分析股票代码（过滤无关对话）
     * @param maxMessages  最多召回条数
     * @return 格式化的上下文文本
     */
    public String getStockContext(String sessionId, String stockCode, int maxMessages) {
        List<ChatMessage> history = getSessionHistory(sessionId, maxMessages);
        if (history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【近期对话上下文 - ").append(stockCode).append("】\n");

        history.stream()
                .filter(msg -> msg.getContent() != null && !msg.getContent().isBlank())
                .forEach(msg -> {
                    String agentName = msg.getAgentType() != null
                            ? msg.getAgentType().getName() : "用户";
                    String snippet = msg.getContent().length() > 150
                            ? msg.getContent().substring(0, 150) + "..."
                            : msg.getContent();
                    sb.append(String.format("• [%s] %s\n", agentName, snippet));
                });

        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有辅助
    // ──────────────────────────────────────────────────────────────────────

    /** LLM 智能摘要 */
    private String generateLlmSummary(List<ChatMessage> history) {
        StringBuilder dialogue = new StringBuilder();
        history.forEach(msg -> {
            String agent = msg.getAgentType() != null ? msg.getAgentType().getName() : "用户";
            dialogue.append("[").append(agent).append("] ")
                    .append(msg.getContent(), 0, Math.min(200, msg.getContent().length()))
                    .append("\n");
        });

        String prompt = "请用100字以内概括以下对话要点，突出关键结论和未解决的问题：\n\n" + dialogue;
        try {
            String result = llmManager.call(
                    "你是一个专业的金融对话摘要助手，请简洁客观地提炼对话要点。",
                    prompt);
            if (result != null && !result.isBlank()) return result;
        } catch (Exception e) {
            log.warn("[MemoryService] LLM 摘要生成失败: {}", e.getMessage());
        }
        return generateTemplateSummary(history);
    }

    /** 模板摘要（无 LLM 时降级） */
    private String generateTemplateSummary(List<ChatMessage> history) {
        StringBuilder summary = new StringBuilder("最近对话摘要：\n");
        history.forEach(msg -> {
            String agentName = msg.getAgentType() != null
                    ? msg.getAgentType().getName() : "用户";
            String snippet = msg.getContent().length() > 100
                    ? msg.getContent().substring(0, 100) + "..."
                    : msg.getContent();
            summary.append(String.format("• [%s] %s\n", agentName, snippet));
        });
        return summary.toString();
    }

    /** 读摘要缓存 */
    private String getSummaryCached(String sessionId) {
        String key = SUMMARY_KEY_PREFIX + sessionId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return cached instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 写摘要缓存 */
    private void saveSummaryCached(String sessionId, String summary) {
        String key = SUMMARY_KEY_PREFIX + sessionId;
        try {
            redisTemplate.opsForValue().set(key, summary, SUMMARY_TTL);
        } catch (Exception e) {
            log.debug("[MemoryService] 摘要缓存写入失败: {}", e.getMessage());
        }
    }
}
