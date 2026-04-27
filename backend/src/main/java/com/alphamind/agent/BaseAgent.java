package com.alphamind.agent;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;

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
