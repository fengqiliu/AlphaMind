package com.alphamind.service;

import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提示词版本管理器 —— 纯内存实现（兼容 dev 无数据库模式）
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>每个 AgentType 维护一个版本列表，最多保留 {@link #MAX_VERSIONS_PER_AGENT} 条历史</li>
 *   <li>支持创建新版本（自动废弃当前 ACTIVE 版本）</li>
 *   <li>支持回滚到任意历史版本</li>
 *   <li>读取当前生效的 Prompt；若无托管版本则返回 {@code null}，由 Agent 使用内置默认</li>
 * </ul>
 *
 * <h3>生产建议</h3>
 * 若需持久化，可将此类替换为基于 JPA 的实现，接口保持不变即可。
 */
@Slf4j
@Service
public class PromptManager {

    private static final int MAX_VERSIONS_PER_AGENT = 20;

    /** AgentType → 按版本号升序排列的版本列表 */
    private final Map<AgentType, List<PromptVersion>> store = new ConcurrentHashMap<>();

    /** 全局版本号计数器（各 Agent 独立） */
    private final Map<AgentType, AtomicInteger> versionCounters = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────────
    // 写操作
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 初始化某 Agent 的默认提示词（第一次调用时生效；若已有版本则忽略）。
     * 建议在各 Agent bean 的 {@code @PostConstruct} 中调用。
     *
     * @param agentType     Agent 类型
     * @param defaultPrompt 默认提示词内容
     */
    public synchronized void initDefault(AgentType agentType, String defaultPrompt) {
        if (store.containsKey(agentType)) {
            return; // 已初始化，不覆盖
        }
        createVersion(agentType, defaultPrompt, "系统默认初始化", "system");
        log.debug("[PromptManager] 初始化 {} 的默认提示词", agentType.getName());
    }

    /**
     * 创建新版本并立即激活（旧的 ACTIVE 自动变为 DEPRECATED）。
     *
     * @return 新建的版本对象
     */
    public synchronized PromptVersion createVersion(AgentType agentType,
                                                     String content,
                                                     String changeLog,
                                                     String createdBy) {
        List<PromptVersion> versions = store.computeIfAbsent(agentType, k -> new ArrayList<>());
        AtomicInteger counter = versionCounters.computeIfAbsent(agentType, k -> new AtomicInteger(0));

        // 废弃当前 ACTIVE 版本
        versions.stream()
                .filter(v -> v.getStatus() == PromptStatus.ACTIVE)
                .forEach(v -> v.setStatus(PromptStatus.DEPRECATED));

        int newVersion = counter.incrementAndGet();
        PromptVersion pv = PromptVersion.builder()
                .agentType(agentType)
                .version(newVersion)
                .content(content)
                .status(PromptStatus.ACTIVE)
                .changeLog(changeLog != null ? changeLog : "")
                .createdBy(createdBy != null ? createdBy : "user")
                .createdAt(LocalDateTime.now())
                .activatedAt(LocalDateTime.now())
                .build();

        versions.add(pv);

        // 超出上限则清除最旧的 DEPRECATED 版本
        if (versions.size() > MAX_VERSIONS_PER_AGENT) {
            versions.stream()
                    .filter(v -> v.getStatus() == PromptStatus.DEPRECATED)
                    .min(Comparator.comparingInt(PromptVersion::getVersion))
                    .ifPresent(versions::remove);
        }

        log.info("[PromptManager] 创建新版本: agent={}, v={}", agentType.getName(), newVersion);
        return pv;
    }

    /**
     * 回滚到指定版本（将其重新激活，当前 ACTIVE 降为 DEPRECATED）。
     *
     * @throws NoSuchElementException 版本不存在时抛出
     */
    public synchronized void rollback(AgentType agentType, int targetVersion) {
        List<PromptVersion> versions = store.getOrDefault(agentType, Collections.emptyList());

        PromptVersion target = versions.stream()
                .filter(v -> v.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "提示词版本不存在: agent=" + agentType.getName() + ", version=" + targetVersion));

        // 废弃当前 ACTIVE
        versions.stream()
                .filter(v -> v.getStatus() == PromptStatus.ACTIVE)
                .forEach(v -> v.setStatus(PromptStatus.DEPRECATED));

        // 激活目标版本
        target.setStatus(PromptStatus.ACTIVE);
        target.setActivatedAt(LocalDateTime.now());

        log.info("[PromptManager] 回滚: agent={}, v={}", agentType.getName(), targetVersion);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 读操作
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 获取当前生效的提示词内容。
     *
     * @return 当前 ACTIVE 版本内容；若无托管版本则返回 {@code null}
     */
    public String getActivePrompt(AgentType agentType) {
        List<PromptVersion> versions = store.get(agentType);
        if (versions == null) return null;

        return versions.stream()
                .filter(v -> v.getStatus() == PromptStatus.ACTIVE)
                .findFirst()
                .map(PromptVersion::getContent)
                .orElse(null);
    }

    /**
     * 获取所有版本列表（按版本号倒序）。
     */
    public List<PromptVersion> getVersionHistory(AgentType agentType) {
        List<PromptVersion> versions = store.getOrDefault(agentType, Collections.emptyList());
        List<PromptVersion> copy = new ArrayList<>(versions);
        copy.sort(Comparator.comparingInt(PromptVersion::getVersion).reversed());
        return Collections.unmodifiableList(copy);
    }

    /**
     * 获取所有已初始化的 AgentType。
     */
    public Set<AgentType> getManagedAgents() {
        return Collections.unmodifiableSet(store.keySet());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 内部数据结构
    // ──────────────────────────────────────────────────────────────────────

    /** 提示词状态 */
    public enum PromptStatus {
        ACTIVE("生效中"),
        DEPRECATED("已废弃");

        private final String label;

        PromptStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 提示词版本实体 */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PromptVersion {
        private AgentType agentType;
        private int version;
        private String content;
        private PromptStatus status;
        private String changeLog;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime activatedAt;
    }
}
