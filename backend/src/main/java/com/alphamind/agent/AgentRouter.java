package com.alphamind.agent;

import com.alphamind.model.enums.AgentType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent消息路由器 - 支持 @AgentName 语法将消息路由到对应Agent
 *
 * <p>支持以下 @mention 格式（不区分大小写）：
 * <ul>
 *   <li>{@code @Market 请问…}  / {@code @MarketAgent 请问…}</li>
 *   <li>{@code @Technical …} / {@code @TechnicalAgent …}</li>
 *   <li>{@code @Sentiment …} / {@code @SentimentAgent …}</li>
 *   <li>{@code @Portfolio …} / {@code @PortfolioAgent …}</li>
 *   <li>{@code @Bull …} / {@code @BullAgent …}</li>
 *   <li>{@code @Bear …} / {@code @BearAgent …}</li>
 *   <li>{@code @Neutral …} / {@code @NeutralAgent …}</li>
 *   <li>{@code @Arbitrator …} / {@code @ArbitratorAgent …}</li>
 * </ul>
 * 无 @mention 时使用调用方传入的 {@code agentType}，默认路由到 {@link AgentType#PORTFOLIO}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final MarketAgent marketAgent;
    private final TechnicalAgent technicalAgent;
    private final SentimentAgent sentimentAgent;
    private final PortfolioAgent portfolioAgent;
    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    /**
     * 匹配 {@code @AgentName <content>} 或 {@code @Name <content>}，
     * Agent 后缀可选，大小写不敏感，内容允许多行。
     */
    private static final Pattern AT_PATTERN = Pattern.compile(
            "@(\\w+?)(?:Agent)?\\s+(.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * 路由消息到对应的 Agent。
     *
     * <ol>
     *   <li>若消息以 {@code @XxxAgent} 开头，解析 @mention 并路由到对应 Agent，
     *       同时将 @mention 前缀从实际内容中去除。</li>
     *   <li>否则使用 {@code fallbackType}（null 时默认为 PORTFOLIO）。</li>
     * </ol>
     *
     * @param message      原始用户消息
     * @param fallbackType 无 @mention 时的备用 AgentType，可为 null
     * @return {@link RouteResult} 包含目标 Agent、最终 AgentType、实际消息内容、是否 @mention
     */
    public RouteResult route(String message, AgentType fallbackType) {
        if (message == null || message.isBlank()) {
            AgentType type = fallbackType != null ? fallbackType : AgentType.PORTFOLIO;
            return new RouteResult(getAgent(type), type, message, false);
        }

        Matcher matcher = AT_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            String mention  = matcher.group(1);
            String content  = matcher.group(2).trim();
            AgentType resolved = resolveAgentType(mention);

            if (resolved != null) {
                log.debug("@mention路由: @{} -> {}", mention, resolved);
                return new RouteResult(getAgent(resolved), resolved, content, true);
            }
            // mention 无法识别时，回退到 fallback
            log.debug("无法识别的 @mention: @{}，回退到 {}", mention, fallbackType);
        }

        AgentType effectiveType = fallbackType != null ? fallbackType : AgentType.PORTFOLIO;
        return new RouteResult(getAgent(effectiveType), effectiveType, message, false);
    }

    /**
     * 将 @mention 中的名称解析为 {@link AgentType}。
     * 依次尝试：枚举名称匹配（MARKET）→ code 匹配（market）。
     */
    private AgentType resolveAgentType(String name) {
        // 1. 尝试 enum name 直接匹配（如 Market → MARKET）
        try {
            return AgentType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        // 2. 尝试 code 匹配（如 market, technical）
        try {
            return AgentType.fromCode(name.toLowerCase());
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private BaseAgent getAgent(AgentType type) {
        return switch (type) {
            case MARKET     -> marketAgent;
            case TECHNICAL  -> technicalAgent;
            case SENTIMENT  -> sentimentAgent;
            case PORTFOLIO  -> portfolioAgent;
            case BULL       -> bullAgent;
            case BEAR       -> bearAgent;
            case NEUTRAL    -> neutralAgent;
            case ARBITRATOR -> arbitratorAgent;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 路由结果 VO
     */
    @Data
    public static class RouteResult {
        /** 目标 Agent 实例 */
        private final BaseAgent agent;
        /** 路由到的 AgentType */
        private final AgentType agentType;
        /** 去除 @mention 前缀后的实际消息内容 */
        private final String content;
        /** 是否触发了 @mention 路由 */
        private final boolean atMention;
    }
}
