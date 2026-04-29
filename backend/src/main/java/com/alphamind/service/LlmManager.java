package com.alphamind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 管理器 —— 多模型热切换 + 自实现轻量级熔断降级
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>管理所有可用 {@link ChatModel} 实例（OpenAI / DeepSeek / Anthropic）</li>
 *   <li>按优先级列表顺序调用，第一个成功即返回</li>
 *   <li>每个模型独立维护 {@link CircuitState}（CLOSED / OPEN / HALF_OPEN）</li>
 *   <li>失败次数达到阈值 → OPEN；冷却期结束 → HALF_OPEN → 试探调用</li>
 *   <li>所有模型不可用时返回 {@code null}，由调用方降级为模板</li>
 * </ul>
 *
 * <h3>依赖策略</h3>
 * 通过 Spring AI 多 Starter 自动配置注入所有可用 {@link ChatModel}，
 * {@code @Autowired(required = false)} 保证无 API Key 时不报错。
 */
@Slf4j
@Service
public class LlmManager {

    // ──────────────────────────────────────────────────────────────────────
    // 熔断器配置常量
    // ──────────────────────────────────────────────────────────────────────

    /** 连续失败几次后开启熔断 */
    private static final int FAILURE_THRESHOLD = 3;

    /** 熔断开启后等待多少毫秒再尝试 HALF_OPEN 试探 */
    private static final long OPEN_DURATION_MS = 30_000L;

    /** 单次 LLM 调用超时(ms)，防止长等 */
    private static final int MAX_RETRIES = 2;

    // ──────────────────────────────────────────────────────────────────────
    // 状态
    // ──────────────────────────────────────────────────────────────────────

    /** 注册的模型列表（名称 → ChatModel） */
    private final Map<String, ChatModel> models = new LinkedHashMap<>();

    /** 每个模型的熔断状态 */
    private final Map<String, ModelState> states = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────────
    // 构造 & 注入
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 注入所有可用的 ChatModel（Spring AI 每个 Starter 会注册一个 Bean）。
     * 使用字段注入 + required=false，保证无配置时正常启动。
     */
    @Autowired(required = false)
    public void setAvailableModels(List<ChatModel> chatModels) {
        if (chatModels == null || chatModels.isEmpty()) {
            log.warn("[LlmManager] 未找到任何 ChatModel Bean，LLM 功能将被禁用");
            return;
        }
        for (ChatModel model : chatModels) {
            String name = model.getClass().getSimpleName();
            if (!models.containsKey(name)) {
                registerModel(name, model);
            }
        }
        log.info("[LlmManager] 已注册 {} 个 LLM 模型: {}", models.size(), models.keySet());
    }

    /**
     * 手动注册额外模型（供 Config 类调用）。
     */
    public void registerModel(String name, ChatModel model) {
        models.put(name, model);
        states.put(name, new ModelState(name));
        log.debug("[LlmManager] 注册模型: {}", name);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 核心调用
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 是否有可用模型。
     */
    public boolean isAvailable() {
        return !models.isEmpty();
    }

    /**
     * 使用所有可用模型按优先级调用，带重试和熔断降级。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 响应文本；所有模型均失败时返回 {@code null}
     */
    public String call(String systemPrompt, String userPrompt) {
        if (models.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, ChatModel> entry : models.entrySet()) {
            String name = entry.getKey();
            ChatModel model = entry.getValue();
            ModelState state = states.get(name);

            if (!state.canCall()) {
                log.debug("[LlmManager] 跳过熔断中的模型: {}", name);
                continue;
            }

            String result = callWithRetry(name, model, systemPrompt, userPrompt);
            if (result != null) {
                state.recordSuccess();
                return result;
            }
        }

        log.warn("[LlmManager] 所有 LLM 模型均不可用，降级为模板");
        return null;
    }

    /**
     * 指定模型调用（忽略熔断，仅用于健康探针）。
     */
    public String callSpecific(String modelName, String systemPrompt, String userPrompt) {
        ChatModel model = models.get(modelName);
        if (model == null) return null;
        return callWithRetry(modelName, model, systemPrompt, userPrompt);
    }

    /**
     * 获取所有模型的健康状态快照（用于监控端点）。
     */
    public List<ModelHealthDTO> getHealthStatus() {
        List<ModelHealthDTO> result = new ArrayList<>();
        for (Map.Entry<String, ModelState> e : states.entrySet()) {
            ModelState s = e.getValue();
            result.add(ModelHealthDTO.builder()
                    .modelName(e.getKey())
                    .state(s.state.name())
                    .failureCount(s.failureCount.get())
                    .successCount(s.successCount.get())
                    .build());
        }
        return result;
    }

    /** 手动重置指定模型的熔断器（运维用） */
    public void resetCircuit(String modelName) {
        ModelState s = states.get(modelName);
        if (s != null) {
            s.reset();
            log.info("[LlmManager] 手动重置熔断器: {}", modelName);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有辅助
    // ──────────────────────────────────────────────────────────────────────

    private String callWithRetry(String name, ChatModel model,
                                  String systemPrompt, String userPrompt) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ChatClient client = ChatClient.builder(model).build();
                String response = client.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();
                log.debug("[LlmManager] 模型 {} 调用成功 (attempt={})", name, attempt);
                return response;
            } catch (Exception e) {
                log.warn("[LlmManager] 模型 {} 第 {} 次调用失败: {}", name, attempt, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    states.get(name).recordFailure();
                }
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 内部类：熔断器状态
    // ──────────────────────────────────────────────────────────────────────

    /** 熔断器状态枚举 */
    public enum CircuitState {
        /** 正常，允许调用 */
        CLOSED,
        /** 熔断开启，拒绝调用 */
        OPEN,
        /** 半开，允许一次试探调用 */
        HALF_OPEN
    }

    /** 单个模型的熔断状态 */
    private static class ModelState {
        final String name;
        volatile CircuitState state = CircuitState.CLOSED;
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        volatile long openedAt = 0;

        ModelState(String name) {
            this.name = name;
        }

        boolean canCall() {
            if (state == CircuitState.CLOSED) return true;
            if (state == CircuitState.OPEN) {
                if (System.currentTimeMillis() - openedAt > OPEN_DURATION_MS) {
                    state = CircuitState.HALF_OPEN;
                    log.info("[Circuit] 模型 {} HALF_OPEN，尝试探活", name);
                    return true;
                }
                return false;
            }
            // HALF_OPEN: 允许一次试探
            return true;
        }

        void recordSuccess() {
            successCount.incrementAndGet();
            failureCount.set(0);
            if (state != CircuitState.CLOSED) {
                log.info("[Circuit] 模型 {} 恢复正常，CLOSED", name);
                state = CircuitState.CLOSED;
            }
        }

        void recordFailure() {
            int failures = failureCount.incrementAndGet();
            if (failures >= FAILURE_THRESHOLD && state == CircuitState.CLOSED) {
                state = CircuitState.OPEN;
                openedAt = System.currentTimeMillis();
                log.warn("[Circuit] 模型 {} 触发熔断，OPEN (failures={})", name, failures);
            } else if (state == CircuitState.HALF_OPEN) {
                // 试探失败，重新打开
                state = CircuitState.OPEN;
                openedAt = System.currentTimeMillis();
                log.warn("[Circuit] 模型 {} 试探失败，重新 OPEN", name);
            }
        }

        void reset() {
            state = CircuitState.CLOSED;
            failureCount.set(0);
            openedAt = 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // DTO
    // ──────────────────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelHealthDTO {
        private String modelName;
        private String state;
        private int failureCount;
        private int successCount;
    }
}
