package com.alphamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AlphaMind应用配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "alphamind")
public class AlphaMindConfig {

    private LlmConfig llm = new LlmConfig();
    private AnalysisConfig analysis = new AnalysisConfig();
    private DebateConfig debate = new DebateConfig();
    private SseConfig sse = new SseConfig();
    private MemoryConfig memory = new MemoryConfig();

    @Data
    public static class LlmConfig {
        private String primaryProvider = "openai";
        private String[] fallbackProviders = {"deepseek", "anthropic"};
        private int timeoutSeconds = 120;
    }

    @Data
    public static class AnalysisConfig {
        private int maxHistoryRecall = 5;
        private double confidenceThreshold = 0.6;
        private int defaultHoldingDays = 30;
    }

    @Data
    public static class DebateConfig {
        private int maxRounds = 3;
        private double votingThreshold = 0.5;
        private double confidenceWeight = 0.4;
    }

    @Data
    public static class SseConfig {
        private int heartbeatIntervalSeconds = 30;
        private boolean reconnectEnabled = true;
    }

    @Data
    public static class MemoryConfig {
        private boolean enabled = true;
        private int ttlHours = 24;
        private int vectorDimensions = 1536;
    }
}
