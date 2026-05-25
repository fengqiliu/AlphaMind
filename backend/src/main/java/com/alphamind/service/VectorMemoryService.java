package com.alphamind.service;

import com.alphamind.model.dto.AnalysisReportDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 向量记忆服务 —— 基于 Spring AI VectorStore（Redis Stack）实现语义历史召回。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li><b>存储</b>：每次分析完成后，将报告摘要转换为自然语言文档，附带股票代码等元数据，
 *       通过 EmbeddingModel 生成向量并写入 Redis Vector Store。</li>
 *   <li><b>召回</b>：Agent 执行前，按股票代码过滤，语义搜索最近 N 条历史分析，
 *       合并为 contextSummary 字符串注入 Agent 上下文（ThreadLocal），
 *       供 LLM 生成投资理由时参考历史趋势。</li>
 * </ol>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>若 {@code VectorStore} Bean 未注入（无 EmbeddingModel）→ 全部方法静默跳过</li>
 *   <li>若运行时操作失败（无 Redis Stack 模块、API 超时等）→ 捕获异常，日志 WARN，返回 null</li>
 * </ul>
 */
@Slf4j
@Service
public class VectorMemoryService {

    /** 股票代码安全校验：仅允许字母和数字，4-8 位 */
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,8}$");
    private static final int DEFAULT_TOP_K = 3;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Spring AI 自动配置的向量存储（需要 Redis Stack 模块 + EmbeddingModel）。
     * 无 Redis Stack 或无 API Key 时 Bean 可能不存在，使用 required=false 保持启动可用性。
     */
    @Autowired(required = false)
    private VectorStore vectorStore;

    // ──────────────────────────────────────────────────────────────────────
    // 公共接口
    // ──────────────────────────────────────────────────────────────────────

    /** 判断向量存储是否可用（Bean 是否注入成功） */
    public boolean isAvailable() {
        return vectorStore != null;
    }

    /**
     * 将分析报告摘要存入向量存储。
     * 失败时仅打印 WARN 日志，不抛出异常。
     *
     * @param report 完整分析报告（至少包含 stockCode）
     */
    public void store(AnalysisReportDTO report) {
        if (!isAvailable() || report == null || report.getStockCode() == null) return;
        if (!isValidStockCode(report.getStockCode())) {
            log.warn("[VectorMemory] 跳过存储：无效股票代码 '{}'", report.getStockCode());
            return;
        }
        try {
            String content = buildContent(report);
            Map<String, Object> metadata = buildMetadata(report);
            Document doc = new Document(content, metadata);
            vectorStore.add(List.of(doc));
            log.info("[VectorMemory] 存储完成: stockCode={}", report.getStockCode());
        } catch (Exception e) {
            log.warn("[VectorMemory] 存储失败（降级跳过）: {}", e.getMessage());
        }
    }

    /**
     * 按股票代码语义召回历史分析摘要。
     *
     * @param stockCode 股票代码
     * @return 格式化的历史摘要字符串（多条用分隔线拼接）；无结果或不可用时返回 null
     */
    public String recall(String stockCode) {
        return recall(stockCode, DEFAULT_TOP_K);
    }

    /**
     * 按股票代码语义召回历史分析摘要（指定召回数量）。
     *
     * @param stockCode 股票代码
     * @param topK      最多召回条数
     * @return 格式化的历史摘要字符串；无结果或不可用时返回 null
     */
    public String recall(String stockCode, int topK) {
        if (!isAvailable() || stockCode == null) return null;
        if (!isValidStockCode(stockCode)) return null;
        try {
            // 按股票代码元数据精确过滤，语义查询描述历史趋势
            SearchRequest request = SearchRequest.builder()
                    .query("股票 " + stockCode + " 历史分析 趋势 信号")
                    .topK(topK)
                    .filterExpression("stockCode == '" + stockCode + "'")
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            if (docs == null || docs.isEmpty()) return null;

            String summary = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("[VectorMemory] 召回 {} 条历史分析: stockCode={}", docs.size(), stockCode);
            return summary;
        } catch (Exception e) {
            log.warn("[VectorMemory] 召回失败（降级跳过）: {}", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ──────────────────────────────────────────────────────────────────────

    private boolean isValidStockCode(String stockCode) {
        return STOCK_CODE_PATTERN.matcher(stockCode).matches();
    }

    /**
     * 将分析报告转为适合向量化的自然语言文本。
     * 涵盖交易信号、置信度、行情数据、技术分析摘要、舆情摘要、仲裁结论。
     */
    private String buildContent(AnalysisReportDTO report) {
        StringBuilder sb = new StringBuilder();

        // 标题行：股票 + 分析日期
        sb.append(String.format("[历史分析] 股票: %s (%s)",
                report.getStockName() != null ? report.getStockName() : report.getStockCode(),
                report.getStockCode()));
        if (report.getCreatedAt() != null) {
            sb.append(" | 日期: ").append(report.getCreatedAt().format(DATE_FMT));
        }

        // 交易信号
        if (report.getTradeSignal() != null) {
            var sig = report.getTradeSignal();
            sb.append(String.format("%n信号: %s | 目标价: ¥%.2f | 止损价: ¥%.2f",
                    sig.getType() != null ? sig.getType().getLabel() : "N/A",
                    sig.getTargetPrice() != null ? sig.getTargetPrice() : 0.0,
                    sig.getStopLoss() != null ? sig.getStopLoss() : 0.0));
            if (sig.getRationale() != null && !sig.getRationale().isBlank()) {
                // 截取前 200 字避免文档过长
                String rationale = sig.getRationale();
                if (rationale.length() > 200) rationale = rationale.substring(0, 200) + "…";
                sb.append(String.format("%n投资理由: %s", rationale));
            }
        }

        // 置信度
        if (report.getConfidence() != null) {
            var conf = report.getConfidence();
            sb.append(String.format("%n置信度: %s (%.0f%%)",
                    conf.getLevel() != null ? conf.getLevel().getLabel() : "N/A",
                    conf.getValue() != null ? conf.getValue() * 100 : 0.0));
        }

        // 行情数据
        if (report.getMarketData() != null) {
            var m = report.getMarketData();
            sb.append(String.format("%n行情: 价格¥%.2f | 涨跌%+.2f%% | PE=%.1f | PB=%.1f",
                    m.getCurrentPrice() != null ? m.getCurrentPrice() : 0.0,
                    m.getChangePercent() != null ? m.getChangePercent() : 0.0,
                    m.getPe() != null ? m.getPe() : 0.0,
                    m.getPb() != null ? m.getPb() : 0.0));
        }

        // 技术分析
        if (report.getTechnicalIndicators() != null) {
            var t = report.getTechnicalIndicators();
            sb.append(String.format("%n技术分析: 综合评分%d/100", t.getTechnicalScore()));
            if (t.getAiInterpretation() != null && !t.getAiInterpretation().isBlank()) {
                String interp = t.getAiInterpretation();
                if (interp.length() > 150) interp = interp.substring(0, 150) + "…";
                sb.append(" | ").append(interp);
            }
        }

        // 舆情分析
        if (report.getSentimentData() != null) {
            var s = report.getSentimentData();
            sb.append(String.format("%n舆情分析: 情绪评分%.2f",
                    s.getSentimentScore() != null ? s.getSentimentScore() : 0.0));
            if (s.getAiSummary() != null && !s.getAiSummary().isBlank()) {
                String summary = s.getAiSummary();
                if (summary.length() > 150) summary = summary.substring(0, 150) + "…";
                sb.append(" | ").append(summary);
            }
        }

        // 辩论仲裁结论（辩论模式）
        if (report.getJudgment() != null) {
            var j = report.getJudgment();
            if (j.getFinalPosition() != null) {
                sb.append(String.format("%n仲裁立场: %s", j.getFinalPosition().name()));
            }
            if (j.getReasoning() != null && !j.getReasoning().isBlank()) {
                String reasoning = j.getReasoning();
                if (reasoning.length() > 150) reasoning = reasoning.substring(0, 150) + "…";
                sb.append(String.format("%n仲裁结论: %s", reasoning));
            }
        }

        return sb.toString();
    }

    /**
     * 构建文档元数据，用于向量过滤查询。
     */
    private Map<String, Object> buildMetadata(AnalysisReportDTO report) {
        String signal = "HOLD";
        if (report.getTradeSignal() != null && report.getTradeSignal().getType() != null) {
            signal = report.getTradeSignal().getType().name();
        }
        return Map.of(
                "stockCode",    report.getStockCode(),
                "stockName",    report.getStockName() != null ? report.getStockName() : "",
                "signal",       signal,
                "analysisDate", report.getCreatedAt() != null ? report.getCreatedAt().format(DATE_FMT) : ""
        );
    }
}
