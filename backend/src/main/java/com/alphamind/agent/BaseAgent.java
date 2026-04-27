package com.alphamind.agent;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent基类 - 所有Agent的抽象基类
 */
@Slf4j
public abstract class BaseAgent {

    protected final AgentType agentType;
    protected final Map<String, Object> context;
    protected String modelName;

    /**
     * Spring AI ChatClient - 可选注入，无 API key 时为 null
     */
    @Autowired(required = false)
    protected ChatClient chatClient;

    protected BaseAgent(AgentType agentType) {
        this.agentType = agentType;
        this.context = new ConcurrentHashMap<>();
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 设置上下文数据
     */
    public void setContext(String key, Object value) {
        context.put(key, value);
    }

    /**
     * 获取上下文数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key) {
        return (T) context.get(key);
    }

    /**
     * 获取上下文数据，带默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, T defaultValue) {
        return (T) context.getOrDefault(key, defaultValue);
    }

    /**
     * 清除上下文
     */
    public void clearContext() {
        context.clear();
    }

    /**
     * 执行分析 - 由子类实现
     */
    public abstract AnalysisReportDTO analyze(AnalysisReportDTO report);

    /**
     * 处理聊天消息
     */
    public abstract ChatMessage chat(ChatMessage userMessage);

    /**
     * 获取Agent提示词
     */
    public abstract String getSystemPrompt();

    /**
     * 获取Agent描述
     */
    public String getDescription() {
        return agentType.getDescription();
    }

    /**
     * 检查LLM是否可用
     */
    protected boolean isLlmAvailable() {
        return chatClient != null;
    }

    /**
     * 调用LLM生成分析文本
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词（包含上下文数据）
     * @return LLM响应文本，失败时返回 null
     */
    protected String llmCall(String systemPrompt, String userPrompt) {
        if (chatClient == null) {
            return null;
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[{}] LLM调用失败，将使用默认响应: {}", agentType.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 记录日志
     */
    protected void logInfo(String message) {
        log.info("[{}] {}", agentType.getName(), message);
    }

    protected void logDebug(String message) {
        log.debug("[{}] {}", agentType.getName(), message);
    }

    protected void logError(String message, Throwable e) {
        log.error("[{}] {}: {}", agentType.getName(), message, e.getMessage(), e);
    }
}
