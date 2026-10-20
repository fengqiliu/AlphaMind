package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.scoring.AnalysisScoringPolicy;
import com.alphamind.scoring.DebateEvidence;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.DebatePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 中立Agent - 保持中立客观分析
 */
@Slf4j
@Component
public class NeutralAgent extends BaseAgent {

    public NeutralAgent() {
        super(AgentType.NEUTRAL);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始中立分析: " + report.getStockCode());
        MarketDataDTO market = getContext("marketData");
        TechnicalIndicatorsDTO tech = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String viewText;
        String llmPrompt = appendNewsHeadlines(DebateEvidence.prompt(market, tech, sentiment,
                DebatePosition.NEUTRAL, "请列出已验证的多空证据与缺口"), sentiment);
        String llmResult = llmCall(getSystemPrompt(), llmPrompt);
        viewText = llmResult != null ? llmResult : generateNeutralAnalysis(market, tech, sentiment);

        List<String> reasons = extractNeutralReasons(market, tech, sentiment);
        ConfidenceDTO evidenceConfidence = AnalysisScoringPolicy.confidence(market, tech, sentiment);

        DebateViewDTO neutralView = DebateViewDTO.builder()
                .position(DebatePosition.NEUTRAL)
                .agentType(AgentType.NEUTRAL)
                .view(viewText)
                .reasons(reasons)
                .keyPoints(reasons.stream().limit(3).toList())
                .targetPrice(null) // 中立不给出目标价
                .upsidePotential(null)
                .confidence(evidenceConfidence)
                .build();

        setContext("neutralView", neutralView);
        return report;
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        TechnicalIndicatorsDTO technical = getContext("technicalIndicators");
        SentimentDataDTO sentiment = getContext("sentimentData");

        String prompt = appendNewsHeadlines(DebateEvidence.prompt(marketData, technical, sentiment,
                DebatePosition.NEUTRAL, userMessage.getContent()), sentiment);

        String response = llmCall(getSystemPrompt(), prompt);
        if (response == null) {
            response = generateNeutralAnalysis(marketData, technical, sentiment);
        }

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你从中立立场检验投资假设，立场不是已被证实的结论。
            只使用提供的行情、技术指标和新闻，逐项区分证据、假设与缺口。
            不编造财报、机构观点、估值合理性、同业排名或宏观环境事实。
            当前指标关系不能证明金叉、死叉或持续变化；没有历史序列证据时不得如此表述。
            允许没有足够论据，不强行凑数；不生成独立目标价、固定止损或收益概率。
            交易计划由策略规则另行生成，只解释已提供内容。
            """;
    }

    public DebatePosition getPosition() {
        return DebatePosition.NEUTRAL;
    }

    public int getVoteWeight() {
        return 1;
    }

    private List<String> extractNeutralReasons(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment) {
        return DebateEvidence.reasons(market, tech, sentiment, DebatePosition.NEUTRAL);
    }

    private String generateNeutralAnalysis(MarketDataDTO market, TechnicalIndicatorsDTO tech, SentimentDataDTO sentiment) {
        return DebateEvidence.render(market, tech, sentiment, DebatePosition.NEUTRAL);
    }
}
