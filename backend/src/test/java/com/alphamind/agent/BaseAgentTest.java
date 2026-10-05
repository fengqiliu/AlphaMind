package com.alphamind.agent;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.dto.SentimentDataDTO;
import com.alphamind.model.enums.AgentType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseAgentTest {

    /** 仅用于测试 BaseAgent 具体辅助方法的最小子类。 */
    private static final class TestAgent extends BaseAgent {
        TestAgent() {
            super(AgentType.SENTIMENT);
        }

        @Override
        public AnalysisReportDTO analyze(AnalysisReportDTO report) {
            return report;
        }

        @Override
        public ChatMessage chat(ChatMessage userMessage) {
            return userMessage;
        }

        @Override
        public String getSystemPrompt() {
            return "test";
        }
    }

    private final BaseAgent agent = new TestAgent();

    @Test
    void appendNewsHeadlinesAddsRealHeadlinesBlock() {
        SentimentDataDTO sentiment = SentimentDataDTO.builder()
                .recentHeadlines(List.of("标题一", "标题二"))
                .build();

        String result = agent.appendNewsHeadlines("原始Prompt", sentiment);

        assertTrue(result.startsWith("原始Prompt"));
        assertTrue(result.contains("最新资讯标题"));
        assertTrue(result.contains("- 标题一"));
        assertTrue(result.contains("- 标题二"));
    }

    @Test
    void appendNewsHeadlinesKeepsPromptUnchangedWithoutNews() {
        assertEquals("原始Prompt", agent.appendNewsHeadlines("原始Prompt", null));
        assertEquals("原始Prompt",
                agent.appendNewsHeadlines("原始Prompt", SentimentDataDTO.builder().build()));
        assertEquals("原始Prompt", agent.appendNewsHeadlines("原始Prompt",
                SentimentDataDTO.builder().recentHeadlines(List.of()).build()));
    }

    @Test
    void totalNewsCountSumsAcrossSourcesAndToleratesMissingData() {
        assertEquals(0, BaseAgent.totalNewsCount(null));

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("eastmoney", 20);
        assertEquals(20, BaseAgent.totalNewsCount(
                SentimentDataDTO.builder().newsCountBySource(counts).build()));

        Map<String, Integer> multi = new LinkedHashMap<>();
        multi.put("eastmoney", 12);
        multi.put("sina", 8);
        multi.put("other", null);
        assertEquals(20, BaseAgent.totalNewsCount(
                SentimentDataDTO.builder().newsCountBySource(multi).build()));
    }
}
