package com.alphamind.controller;

import com.alphamind.model.dto.ApiResponse;
import com.alphamind.model.enums.AgentType;
import com.alphamind.service.LlmManager;
import com.alphamind.service.PromptManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工程能力管理端点
 *
 * <ul>
 *   <li>{@code /api/v1/admin/prompts/*} — 提示词版本管理</li>
 *   <li>{@code /api/v1/admin/llm/*}     — LLM 健康状态 & 熔断重置</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PromptManager promptManager;
    private final LlmManager llmManager;

    // ──────────────────────────────────────────────────────────────────────
    // 提示词管理
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 获取所有已托管 AgentType 列表。
     * GET /api/v1/admin/prompts
     */
    @GetMapping("/prompts")
    public ApiResponse<Set<AgentType>> listManagedAgents() {
        return ApiResponse.success(promptManager.getManagedAgents());
    }

    /**
     * 获取指定 Agent 当前生效的提示词。
     * GET /api/v1/admin/prompts/{agentType}
     */
    @GetMapping("/prompts/{agentType}")
    public ApiResponse<String> getActivePrompt(@PathVariable AgentType agentType) {
        String prompt = promptManager.getActivePrompt(agentType);
        if (prompt == null) {
            return ApiResponse.error(404, "该 Agent 尚无托管提示词: " + agentType.getName());
        }
        return ApiResponse.success(prompt);
    }

    /**
     * 获取指定 Agent 的历史版本列表（倒序）。
     * GET /api/v1/admin/prompts/{agentType}/versions
     */
    @GetMapping("/prompts/{agentType}/versions")
    public ApiResponse<List<PromptManager.PromptVersion>> getVersionHistory(
            @PathVariable AgentType agentType) {
        return ApiResponse.success(promptManager.getVersionHistory(agentType));
    }

    /**
     * 创建新版本并立即激活。
     * POST /api/v1/admin/prompts/{agentType}
     * Body: { "content": "...", "changeLog": "...", "createdBy": "..." }
     */
    @PostMapping("/prompts/{agentType}")
    public ApiResponse<PromptManager.PromptVersion> createVersion(
            @PathVariable AgentType agentType,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ApiResponse.error(400, "content 不能为空");
        }
        PromptManager.PromptVersion version = promptManager.createVersion(
                agentType,
                content,
                body.getOrDefault("changeLog", ""),
                body.getOrDefault("createdBy", "api"));
        log.info("创建提示词新版本: agent={}, v={}", agentType.getName(), version.getVersion());
        return ApiResponse.success(version);
    }

    /**
     * 回滚到指定版本。
     * POST /api/v1/admin/prompts/{agentType}/rollback/{version}
     */
    @PostMapping("/prompts/{agentType}/rollback/{version}")
    public ApiResponse<Void> rollback(
            @PathVariable AgentType agentType,
            @PathVariable int version) {
        try {
            promptManager.rollback(agentType, version);
            log.info("回滚提示词: agent={}, version={}", agentType.getName(), version);
            return ApiResponse.success(null);
        } catch (java.util.NoSuchElementException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // LLM 健康状态
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 获取所有模型的熔断状态。
     * GET /api/v1/admin/llm/health
     */
    @GetMapping("/llm/health")
    public ApiResponse<List<LlmManager.ModelHealthDTO>> getLlmHealth() {
        return ApiResponse.success(llmManager.getHealthStatus());
    }

    /**
     * 手动重置指定模型的熔断器（运维用）。
     * POST /api/v1/admin/llm/{modelName}/reset
     */
    @PostMapping("/llm/{modelName}/reset")
    public ApiResponse<Void> resetCircuit(@PathVariable String modelName) {
        llmManager.resetCircuit(modelName);
        return ApiResponse.success(null);
    }
}
