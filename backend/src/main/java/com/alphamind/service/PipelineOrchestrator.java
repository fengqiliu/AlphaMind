package com.alphamind.service;

import com.alphamind.agent.*;
import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 流水线编排器 - 协调多个Agent顺序执行分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final MarketAgent marketAgent;
    private final TechnicalAgent technicalAgent;
    private final SentimentAgent sentimentAgent;
    private final PortfolioAgent portfolioAgent;
    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    /**
     * 执行流水线分析
     */
    public AnalysisReportDTO execute(
            String stockCode,
            String stockName,
            StrategyType strategy,
            boolean enableDebate,
            Consumer<SSEEvent> eventConsumer) {

        log.info("开始流水线分析: stockCode={}, strategy={}, enableDebate={}",
                stockCode, strategy, enableDebate);

        AnalysisReportDTO report = AnalysisReportDTO.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .build();

        try {
            // 设置策略
            strategy = strategy != null ? strategy : StrategyType.BALANCED;

            // Stage 1: Market Agent
            emit(eventConsumer, AnalysisStage.MARKET, "正在采集行情数据...");
            report = marketAgent.analyze(report);
            initializeAgentContext(marketAgent, report, strategy);
            report = technicalAgent.analyze(report);
            initializeAgentContext(technicalAgent, report, strategy);
            report = sentimentAgent.analyze(report);
            initializeAgentContext(sentimentAgent, report, strategy);

            // Stage 2: Technical Agent
            emit(eventConsumer, AnalysisStage.TECHNICAL, "正在进行技术分析...");
            report = technicalAgent.analyze(report);

            // Stage 3: Sentiment Agent
            emit(eventConsumer, AnalysisStage.SENTIMENT, "正在分析舆情...");
            report = sentimentAgent.analyze(report);

            // Stage 4: Portfolio Agent
            emit(eventConsumer, AnalysisStage.PORTFOLIO, "正在生成投资建议...");
            report = portfolioAgent.analyze(report);

            // Stage 5: Debate (optional)
            if (enableDebate) {
                emit(eventConsumer, AnalysisStage.DEBATE, "正在进行多空辩论...");

                // Initialize debate agents
                initializeDebateAgents(report);

                // Run debate
                bullAgent.chat(ChatMessage.builder().content("分析").build());
                bearAgent.chat(ChatMessage.builder().content("分析").build());
                neutralAgent.chat(ChatMessage.builder().content("分析").build());

                // Arbitrator decides
                report = arbitratorAgent.analyze(report);
            }

            // Complete
            emitComplete(eventConsumer);

            log.info("流水线分析完成: stockCode={}, signal={}",
                    stockCode, report.getTradeSignal().getType());

            return report;

        } catch (Exception e) {
            log.error("流水线分析失败: stockCode={}", stockCode, e);
            emitError(eventConsumer, e.getMessage());
            throw new RuntimeException("分析失败: " + e.getMessage(), e);
        }
    }

    private void initializeAgentContext(BaseAgent agent, AnalysisReportDTO report, StrategyType strategy) {
        agent.setContext("stockCode", report.getStockCode());
        agent.setContext("stockName", report.getStockName());
        agent.setContext("strategy", strategy);
        if (report.getMarketData() != null) {
            agent.setContext("marketData", report.getMarketData());
        }
        if (report.getTechnicalIndicators() != null) {
            agent.setContext("technicalIndicators", report.getTechnicalIndicators());
        }
        if (report.getSentimentData() != null) {
            agent.setContext("sentimentData", report.getSentimentData());
        }
    }

    private void initializeDebateAgents(AnalysisReportDTO report) {
        initializeAgentContext(bullAgent, report, null);
        initializeAgentContext(bearAgent, report, null);
        initializeAgentContext(neutralAgent, report, null);
    }

    private void emit(Consumer<SSEEvent> consumer, AnalysisStage stage, String message) {
        if (consumer != null) {
            consumer.accept(SSEEvent.stageEvent(stage, message));
        }
    }

    private void emitComplete(Consumer<SSEEvent> consumer) {
        if (consumer != null) {
            consumer.accept(SSEEvent.completeEvent());
        }
    }

    private void emitError(Consumer<SSEEvent> consumer, String message) {
        if (consumer != null) {
            consumer.accept(SSEEvent.errorEvent(message));
        }
    }
}
