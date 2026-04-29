package com.alphamind.service;

import com.alphamind.agent.*;
import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.AnalysisStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 辩论编排器 - 按 Bull → Bear → Neutral → Arbitrator 顺序执行辩论，
 * 每个 Agent 完成后向客户端推送 SSE 进度事件。
 *
 * <p>采用顺序（非并行）执行，避免单例 Agent 共享可变 context 时的并发冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebateOrchestrator {

    private final BullAgent bullAgent;
    private final BearAgent bearAgent;
    private final NeutralAgent neutralAgent;
    private final ArbitratorAgent arbitratorAgent;

    /**
     * 执行完整辩论流程。
     *
     * <ol>
     *   <li>初始化各 Agent 上下文（来自 report 中已有数据）</li>
     *   <li>顺序调用 Bull / Bear / Neutral 的 {@code analyze()} 生成各自 {@link DebateViewDTO}</li>
     *   <li>将三方观点注入 ArbitratorAgent 上下文</li>
     *   <li>调用 ArbitratorAgent 生成 {@link JudgmentDTO} 并写入 report</li>
     *   <li>每步完成后通过 {@code eventConsumer} 推送 SSE 事件</li>
     * </ol>
     *
     * @param report        已完成 Pipeline 前四阶段的报告（含 marketData/technical/sentiment/tradeSignal）
     * @param eventConsumer SSE 事件回调，可为 null（同步模式）
     * @return 填充了 {@code judgment} 和 {@code debateViews} 的报告
     */
    public AnalysisReportDTO runDebate(AnalysisReportDTO report, Consumer<SSEEvent> eventConsumer) {
        log.info("启动辩论系统: stockCode={}", report.getStockCode());

        // ── Step 1: 多头分析师 ────────────────────────────────────────────────
        emitStage(eventConsumer, "多头分析师正在生成看多观点...");
        initContext(bullAgent, report);
        report = bullAgent.analyze(report);
        DebateViewDTO bullView = bullAgent.getContext("bullView");
        emitData(eventConsumer, AgentType.BULL, bullView);
        log.debug("多头观点生成完成");

        // ── Step 2: 空头分析师 ────────────────────────────────────────────────
        emitStage(eventConsumer, "空头分析师正在生成看空观点...");
        initContext(bearAgent, report);
        report = bearAgent.analyze(report);
        DebateViewDTO bearView = bearAgent.getContext("bearView");
        emitData(eventConsumer, AgentType.BEAR, bearView);
        log.debug("空头观点生成完成");

        // ── Step 3: 中立分析师 ────────────────────────────────────────────────
        emitStage(eventConsumer, "中立分析师正在进行客观评估...");
        initContext(neutralAgent, report);
        report = neutralAgent.analyze(report);
        DebateViewDTO neutralView = neutralAgent.getContext("neutralView");
        emitData(eventConsumer, AgentType.NEUTRAL, neutralView);
        log.debug("中立观点生成完成");

        // 汇总三方观点到报告
        List<DebateViewDTO> views = new ArrayList<>();
        if (bullView != null)    views.add(bullView);
        if (bearView != null)    views.add(bearView);
        if (neutralView != null) views.add(neutralView);
        report.setDebateViews(views);

        // ── Step 4: 仲裁官裁决 ────────────────────────────────────────────────
        emitStage(eventConsumer, "仲裁官正在综合各方观点做出最终裁决...");
        initContext(arbitratorAgent, report);
        // 将三方观点注入仲裁官上下文（用于 LLM 增强推理）
        arbitratorAgent.setContext("bullView",    bullView);
        arbitratorAgent.setContext("bearView",    bearView);
        arbitratorAgent.setContext("neutralView", neutralView);
        report = arbitratorAgent.analyze(report);
        emitData(eventConsumer, AgentType.ARBITRATOR, report.getJudgment());

        log.info("辩论流程完成，最终立场: {}",
                report.getJudgment() != null ? report.getJudgment().getFinalPosition() : "N/A");
        return report;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    /** 初始化 Agent 上下文（复制 report 中的分析数据） */
    private void initContext(BaseAgent agent, AnalysisReportDTO report) {
        agent.clearContext();
        agent.setContext("stockCode", report.getStockCode());
        agent.setContext("stockName", report.getStockName());
        if (report.getMarketData() != null)
            agent.setContext("marketData", report.getMarketData());
        if (report.getTechnicalIndicators() != null)
            agent.setContext("technicalIndicators", report.getTechnicalIndicators());
        if (report.getSentimentData() != null)
            agent.setContext("sentimentData", report.getSentimentData());
        if (report.getTradeSignal() != null)
            agent.setContext("tradeSignal", report.getTradeSignal());
        if (report.getConfidence() != null)
            agent.setContext("confidence", report.getConfidence());
    }

    private void emitStage(Consumer<SSEEvent> consumer, String message) {
        if (consumer != null) {
            consumer.accept(SSEEvent.stageEvent(AnalysisStage.DEBATE, message));
        }
    }

    private void emitData(Consumer<SSEEvent> consumer, AgentType agentType, Object data) {
        if (consumer != null && data != null) {
            consumer.accept(SSEEvent.dataEvent(agentType, data));
        }
    }
}
