package com.alphamind.agent;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.enums.AgentType;
import com.alphamind.service.LlmManager;
import com.alphamind.service.PromptManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 基类 —— 所有 Agent 的抽象父类
 *
 * <p>核心机制：
 * <ul>
 *   <li>通过 {@link LlmManager} 实现多模型热切换 + 熔断降级（优先），
 *       回退到单个 {@link ChatClient}（兼容旧注入方式），最终降级为模板输出。</li>
 *   <li>通过 {@link PromptManager} 实现运行时提示词版本管理；
 *       Agent 启动时自动将 {@link #getSystemPrompt()} 注册为默认版本。</li>
 * </ul>
 */
@Slf4j
public abstract class BaseAgent {

    protected final AgentType agentType;
    protected final Map<String, Object> context;
    protected String modelName;

    // ── LLM 调用链（优先级：LlmManager > ChatClient > null）────────────────

    /** 多模型管理器（可选注入） */
    @Autowired(required = false)
    @Lazy
    protected LlmManager llmManager;

    /** 单 ChatClient 兼容注入（无 LlmManager 时使用） */
    @Autowired(required = false)
    protected ChatClient chatClient;

    /** 提示词版本管理器（可选注入） */
    @Autowired(required = false)
    @Lazy
    protected PromptManager promptManager;

    protected BaseAgent(AgentType agentType) {
        this.agentType = agentType;
        this.context = new ConcurrentHashMap<>();
    }

    /**
     * Bean 初始化后自动向 PromptManager 注册默认提示词（若可用）。
     */
    @PostConstruct
    public void registerDefaultPrompt() {
        if (promptManager != null) {
            promptManager.initDefault(agentType, getSystemPrompt());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Context 管理
    // ──────────────────────────────────────────────────────────────────────

    public AgentType getAgentType() { return agentType; }

    public String getModelName() { return modelName; }

    public void setModelName(String modelName) { this.modelName = modelName; }

    public void setContext(String key, Object value) { context.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T getContext(String key) { return (T) context.get(key); }

    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, T defaultValue) {
        return (T) context.getOrDefault(key, defaultValue);
    }

    public void clearContext() { context.clear(); }

    // ──────────────────────────────────────────────────────────────────────
    // 抽象接口
    // ──────────────────────────────────────────────────────────────────────

    public abstract AnalysisReportDTO analyze(AnalysisReportDTO report);

    public abstract ChatMessage chat(ChatMessage userMessage);

    /** 子类返回内置默认提示词（PromptManager 无托管版本时使用） */
    public abstract String getSystemPrompt();

    public String getDescription() { return agentType.getDescription(); }

    // ──────────────────────────────────────────────────────────────────────
    // LLM 调用
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 检查 LLM 是否可用（LlmManager 或 ChatClient 任意一个可用即可）。
     */
    protected boolean isLlmAvailable() {
        return (llmManager != null && llmManager.isAvailable()) || chatClient != null;
    }

    /**
     * 获取当前生效的系统提示词。
     * 优先使用 PromptManager 中的托管版本，回退到子类的内置默认值。
     */
    protected String getEffectiveSystemPrompt() {
        if (promptManager != null) {
            String managed = promptManager.getActivePrompt(agentType);
            if (managed != null) return managed;
        }
        return getSystemPrompt();
    }

    /**
     * 调用 LLM 生成文本。
     *
     * <p>调用链：LlmManager（多模型熔断降级） → 单 ChatClient → {@code null}（触发模板降级）。
     * 系统提示词自动从 {@link #getEffectiveSystemPrompt()} 获取，子类传入的 systemPrompt
     * 仅在 PromptManager 无托管版本时使用。
     *
     * @param systemPrompt 子类提供的系统提示词（可被 PromptManager 覆盖）
     * @param userPrompt   用户提示词
     * @return LLM 响应文本；失败或不可用时返回 {@code null}
     */
    protected String llmCall(String systemPrompt, String userPrompt) {
        // 取有效提示词（PromptManager 托管版本优先）
        String effectiveSystem = promptManager != null
                ? promptManager.getActivePrompt(agentType)
                : null;
        if (effectiveSystem == null) effectiveSystem = systemPrompt;

        // 优先使用 LlmManager（多模型 + 熔断）
        if (llmManager != null && llmManager.isAvailable()) {
            String result = llmManager.call(effectiveSystem, userPrompt);
            if (result != null) return result;
        }

        // 降级：单 ChatClient
        if (chatClient != null) {
            try {
                return chatClient.prompt()
                        .system(effectiveSystem)
                        .user(userPrompt)
                        .call()
                        .content();
            } catch (Exception e) {
                log.warn("[{}] ChatClient 调用失败: {}", agentType.getName(), e.getMessage());
            }
        }

        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 日志辅助
    // ──────────────────────────────────────────────────────────────────────

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

