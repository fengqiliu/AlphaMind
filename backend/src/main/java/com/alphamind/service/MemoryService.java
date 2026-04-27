package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String SESSION_KEY_PREFIX = "alphamind:session:";
    private static final String HISTORY_KEY_PREFIX = "alphamind:history:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final Map<String, List<ChatMessage>> localMemory = new ConcurrentHashMap<>();

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
     * 获取最近的上下文摘要
     */
    public String getContextSummary(String sessionId, int maxMessages) {
        List<ChatMessage> history = getSessionHistory(sessionId, maxMessages);
        if (history.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("最近的对话历史：\n");
        history.forEach(msg -> {
            summary.append(String.format("- [%s] %s: %s\n",
                    msg.getAgentType() != null ? msg.getAgentType().getName() : "User",
                    msg.getRole(),
                    msg.getContent().substring(0, Math.min(100, msg.getContent().length()))));
        });
        return summary.toString();
    }
}
