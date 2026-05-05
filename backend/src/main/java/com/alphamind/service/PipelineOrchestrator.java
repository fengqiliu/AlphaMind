package com.alphamind.service;

import com.alphamind.agent.*;
import com.alphamind.model.dto.*;
import com.alphamind.model.enums.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
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
    private final DebateOrchestrator debateOrchestrator;

    /**
     * 执行流水线分析
     */
    public AnalysisReportDTO execute(
            String stockCode,
            String stockName,
            StrategyType strategy,
            AnalysisMode mode,
            Consumer<SSEEvent> eventConsumer) {

        final AnalysisMode effectiveMode = mode != null ? mode : AnalysisMode.DEBATE;
        final boolean enableDebate = effectiveMode == AnalysisMode.DEBATE;

        log.info("开始流水线分析: stockCode={}, strategy={}, mode={}, enableDebate={}",
            stockCode, strategy, effectiveMode, enableDebate);

        AnalysisReportDTO report = AnalysisReportDTO.builder()
                .id(UUID.randomUUID().toString())
                .stockCode(stockCode)
                .stockName(stockName)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            // 设置策略
            final StrategyType finalStrategy = strategy != null ? strategy : StrategyType.BALANCED;

            // Stage 1: Market Agent - 采集行情数据
            emit(eventConsumer, AnalysisStage.MARKET, "正在采集行情数据...");
            initializeAgentContext(marketAgent, report, finalStrategy);
            report = marketAgent.analyze(report);
            emitData(eventConsumer, AgentType.MARKET, report.getMarketData());

            // Stage 2: Technical Agent - 技术指标分析
            emit(eventConsumer, AnalysisStage.TECHNICAL, "正在进行技术分析...");
            initializeAgentContext(technicalAgent, report, finalStrategy);
            report = technicalAgent.analyze(report);
            emitData(eventConsumer, AgentType.TECHNICAL, report.getTechnicalIndicators());

            // Stage 3: Sentiment Agent - 舆情分析
            emit(eventConsumer, AnalysisStage.SENTIMENT, "正在分析舆情数据...");
            initializeAgentContext(sentimentAgent, report, finalStrategy);
            report = sentimentAgent.analyze(report);
            emitData(eventConsumer, AgentType.SENTIMENT, report.getSentimentData());

            // Stage 4: Portfolio Agent - 综合决策
            emit(eventConsumer, AnalysisStage.PORTFOLIO, "正在生成投资建议...");
            initializeAgentContext(portfolioAgent, report, finalStrategy);
            report = portfolioAgent.analyze(report);
            emitData(eventConsumer, AgentType.PORTFOLIO, report.getTradeSignal());

            // Stage 5: Debate (optional) - 多空辩论
            if (enableDebate) {
                emit(eventConsumer, AnalysisStage.DEBATE, "正在进行多空辩论...");
                report = debateOrchestrator.runDebate(report, eventConsumer);
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
        } finally {
            // 确保无论成功还是异常，都释放所有 Agent 的 ThreadLocal，防止线程池泄漏
            marketAgent.clearContext();
            technicalAgent.clearContext();
            sentimentAgent.clearContext();
            portfolioAgent.clearContext();
        }
    }

    private void emitData(Consumer<SSEEvent> consumer, AgentType agentType, Object data) {
        if (consumer != null && data != null) {
            consumer.accept(SSEEvent.dataEvent(agentType, data));
        }
    }

    private void initializeAgentContext(BaseAgent agent, AnalysisReportDTO report, StrategyType strategy) {
        agent.clearContext();
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
