package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.news.service.HeadlineSentimentScorer;
import com.alphamind.news.service.NewsResolution;
import com.alphamind.news.service.StockNewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 舆情Agent - 负责分析市场舆情和情绪
 */
@Slf4j
@Component
public class SentimentAgent extends BaseAgent {

    /** 参与摘要与展示的新闻标题条数。 */
    private static final int NEWS_HEADLINE_LIMIT = 5;

    private final StockNewsService stockNewsService;

    public SentimentAgent(StockNewsService stockNewsService) {
        super(AgentType.SENTIMENT);
        this.stockNewsService = stockNewsService;
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始舆情分析: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            if (marketData == null) {
                throw new RuntimeException("缺少市场数据");
            }

            // 真实新闻源（失败时为 empty，舆情分析继续但不编造新闻）
            Optional<NewsResolution> news = stockNewsService.news(report.getStockCode())
                    .filter(resolution -> !resolution.snapshot().articles().isEmpty());

            // 分析舆情数据
            SentimentDataDTO sentimentData = analyzeSentiment(marketData, news);

            // 调用LLM生成舆情综合摘要
            String aiSummary = generateSentimentSummary(sentimentData, marketData);
            sentimentData.setAiSummary(aiSummary);

            report.setSentimentData(sentimentData);
            setContext("sentimentData", sentimentData);

            logInfo("舆情分析完成，情感评分: " + sentimentData.getSentimentScore());
            return report;
        } catch (Exception e) {
            logError("舆情分析失败", e);
            throw new RuntimeException("舆情分析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        SentimentDataDTO sentimentData = getContext("sentimentData");
        if (sentimentData == null) {
            return buildMsg("暂无舆情数据，请先发起分析。");
        }

        // 优先使用LLM生成回复
        String response = llmCall(getSystemPrompt(), buildSentimentPrompt(sentimentData, userMessage.getContent()));
        if (response == null) {
            response = buildSentimentTemplateResponse(sentimentData);
        }
        return buildMsg(response);
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名专业的市场舆情分析师，负责分析股票相关的新闻、研报、社交媒体等舆情信息。

            你的职责：
            1. 分析提供的股票新闻标题与行情代理信号
            2. 不推测未提供的研报评级、目标价或机构观点
            3. 不把行情代理评分当作社交媒体或媒体关注度实测
            4. 识别潜在的利好和利空因素
            5. 明确资讯缺失、过期和行情代理指标的限制

            舆情评分标准：
            - 65分及以上: 稳中向好
            - 50至不足65分: 震荡观望
            - 35至不足50分: 谨慎悲观
            - 不足35分: 明显看空

            回答要求：
            - 明确评分为启发式代理指标，不是收益概率或个人模型决策
            - 缺失PE不等于亏损，不凭绝对PE推断历史低位或安全边际
            - 客观呈现利好和利空因素
            - 给出舆情总结和趋势判断
            """;
    }

    private String generateSentimentSummary(SentimentDataDTO data, MarketDataDTO market) {
        StringBuilder promptBuilder = new StringBuilder(String.format(
                "请简要分析 %s 的市场舆情：舆情评分%.0f/100，趋势:%s，利好%d项，利空%d项。评分依据：%s。数据缺口：%s",
                market.getStockName(), data.getSentimentScore() * 100, data.getSentimentTrend(),
                data.getPositiveFactors().size(), data.getNegativeFactors().size(),
                scoreExplanation(data), dataGapDescription(data)));
        if (data.getRecentHeadlines() != null && !data.getRecentHeadlines().isEmpty()) {
            promptBuilder.append("。以下是最新新闻标题，请结合它们给出贴合实际消息面的判断：");
            data.getRecentHeadlines().forEach(title -> promptBuilder.append("\n- ").append(title));
        }
        String result = llmCall(getSystemPrompt(), promptBuilder.toString());
        return result != null ? result : data.getAnalysisSummary();
    }

    private String buildSentimentPrompt(SentimentDataDTO data, String question) {
        StringBuilder promptBuilder = new StringBuilder(String.format("""
                舆情数据：
                - 舆情评分: %.0f/100
                - 舆情趋势: %s
                - 利好因素: %s
                - 利空因素: %s
                - 评分依据: %s
                - 数据缺口: %s
                """,
                data.getSentimentScore() * 100, data.getSentimentTrend(),
                String.join("；", data.getPositiveFactors()),
                String.join("；", data.getNegativeFactors()),
                scoreExplanation(data), dataGapDescription(data)));
        if (data.getRecentHeadlines() != null && !data.getRecentHeadlines().isEmpty()) {
            promptBuilder.append("\n最新新闻标题：");
            data.getRecentHeadlines().forEach(title -> promptBuilder.append("\n- ").append(title));
        }
        promptBuilder.append("\n\n用户问题：").append(question);
        return promptBuilder.toString();
    }

    private String buildSentimentTemplateResponse(SentimentDataDTO data) {
        StringBuilder sb = new StringBuilder("**舆情分析报告**\n\n");
        sb.append(String.format("**舆情评分**: %.0f/100\n", data.getSentimentScore() * 100));
        sb.append("**舆情趋势**: ").append(data.getSentimentTrend()).append("\n\n");
        if (!data.getPositiveFactors().isEmpty()) {
            sb.append("**利好因素**:\n");
            data.getPositiveFactors().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
        if (!data.getNegativeFactors().isEmpty()) {
            sb.append("**利空因素**:\n");
            data.getNegativeFactors().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }
        sb.append("**评分依据**: ").append(scoreExplanation(data)).append("\n");
        sb.append("**数据缺口**: ").append(dataGapDescription(data)).append("\n\n");
        if (data.getRecentHeadlines() != null && !data.getRecentHeadlines().isEmpty()) {
            sb.append("**最新资讯**:\n");
            data.getRecentHeadlines().forEach(title -> sb.append("- ").append(title).append("\n"));
            sb.append("\n");
        }
        sb.append("**总结**: ").append(data.getAnalysisSummary());
        return sb.toString();
    }

    private ChatMessage buildMsg(String content) {
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * 行情代理信号的启发式评分，不是新闻情绪实测、收益概率或个人模型结论。
     * 原始权重：涨跌幅40%、正PE估值25%、换手率20%、量能15%。
     * 缺失项不参与，按可用项权重归一化；负PE仅作为非正估值单列，不推断亏损原因。
     * 存在新闻标题时，行情占88%，标题关键词信号占12%。
     */
    private SentimentDataDTO analyzeSentiment(
            MarketDataDTO marketData, Optional<NewsResolution> news) {
        Double changePct = finite(marketData.getChangePercent());
        Double pe = finite(marketData.getPe());
        Double turnoverRate = finite(marketData.getTurnoverRate());
        if (turnoverRate != null && turnoverRate < 0) turnoverRate = null;
        String valuationStatus = pe == null || pe == 0 ? "MISSING" : pe < 0 ? "NON_POSITIVE" : "POSITIVE";
        List<String> dataGaps = new ArrayList<>();
        if (changePct == null) dataGaps.add("涨跌幅缺失，未参与评分");
        if (valuationStatus.equals("MISSING")) dataGaps.add("PE缺失或为零，未参与估值评分，不代表亏损");
        if (turnoverRate == null) dataGaps.add("换手率缺失，未参与评分");
        if (marketData.getMarketCap() == null || marketData.getMarketCap() <= 0)
            dataGaps.add("总市值缺失，未推算机构关注或流动性");
        dataGaps.add("无媒体关注度实测来源，未生成关注度百分比");

        // 新闻标题情绪信号：确定性关键词评估，无真实新闻时为 null（评分退回纯行情信号）
        HeadlineSentimentScorer.HeadlineSignal headlineSignal = news
                .map(resolution -> HeadlineSentimentScorer.evaluate(resolution.snapshot().articles()))
                .orElse(null);

        // ---- 各信号评分 ----
        // 涨跌幅：-10% → 0.0, 0% → 0.5, +10% → 1.0
        Double priceSignal = changePct == null ? null : Math.max(0.0, Math.min(1.0, 0.5 + changePct / 20.0));

        // PE估值
        Double peSignal;
        if (valuationStatus.equals("MISSING")) {
            peSignal = null;
        } else if (pe < 0) {
            peSignal = 0.45; // 负PE，与缺失项分开处理
        } else if (pe < 15) {
            peSignal = 0.72; // 低估值
        } else if (pe <= 35) {
            peSignal = 0.60; // 合理估值
        } else if (pe <= 50) {
            peSignal = 0.48; // 偏贵
        } else {
            peSignal = 0.32; // 高估
        }

        // 换手率：1~3% 活跃→0.65；>5% 过热→0.45；<0.5% 低迷→0.40
        Double turnoverSignal;
        if (turnoverRate == null) {
            turnoverSignal = null;
        } else if (turnoverRate < 0.5) {
            turnoverSignal = 0.40;
        } else if (turnoverRate <= 3.0) {
            turnoverSignal = 0.40 + turnoverRate * 0.083; // 0.5→0.44, 3.0→0.65
        } else if (turnoverRate <= 5.0) {
            turnoverSignal = 0.65 - (turnoverRate - 3.0) * 0.05; // 3→0.65, 5→0.55
        } else {
            turnoverSignal = Math.max(0.35, 0.55 - (turnoverRate - 5.0) * 0.04);
        }

        // 只使用连续20日完整且非负的成交量，零分母不生成量比。
        Double volumeSignal = null;
        List<Long> volumes = marketData.getKlineVolumes();
        if (volumes != null && volumes.size() >= 20) {
            List<Long> recent = volumes.subList(volumes.size() - 20, volumes.size());
            if (recent.stream().allMatch(value -> value != null && value >= 0)) {
                double avg20 = recent.stream().mapToDouble(Long::doubleValue).average().orElse(0);
                double avg5 = recent.subList(15, 20).stream().mapToDouble(Long::doubleValue).average().orElse(0);
                if (avg20 > 0) {
                    double ratio = avg5 / avg20;
                    if (ratio < 0.8) volumeSignal = 0.38;
                    else if (ratio <= 1.5) volumeSignal = 0.55 + (ratio - 0.8) / 0.7 * 0.15;
                    else if (ratio <= 2.5) volumeSignal = 0.70 - (ratio - 1.5) * 0.10;
                    else volumeSignal = 0.45;
                }
            }
        }
        if (volumeSignal == null) dataGaps.add("近20日成交量不足、无效或全零，未参与量能评分");
        if (headlineSignal == null) dataGaps.add("新闻源不可用，仅使用行情代理信号");

        Map<String, Double> signals = new LinkedHashMap<>();
        Map<String, Double> weights = new LinkedHashMap<>();
        if (priceSignal != null) { signals.put("涨跌幅", priceSignal); weights.put("涨跌幅", 0.40); }
        if (peSignal != null) { signals.put("PE", peSignal); weights.put("PE", 0.25); }
        if (turnoverSignal != null) { signals.put("换手率", turnoverSignal); weights.put("换手率", 0.20); }
        if (volumeSignal != null) { signals.put("量能", volumeSignal); weights.put("量能", 0.15); }
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double weightedScore = signals.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * weights.get(entry.getKey())).sum();
        double marketScore = totalWeight > 0 ? weightedScore / totalWeight : 0.5;
        double sentimentScore = headlineSignal == null ? marketScore
                : totalWeight > 0 ? marketScore * 0.88 + headlineSignal.score() * 0.12 : headlineSignal.score();
        sentimentScore = Math.round(Math.max(0.10, Math.min(0.95, sentimentScore)) * 100) / 100.0;
        List<String> weightDescriptions = new ArrayList<>();
        double marketShare = headlineSignal == null ? 1.0 : 0.88;
        weights.forEach((name, weight) -> weightDescriptions.add(String.format(java.util.Locale.ROOT,
                "%s %.1f%%", name, weight / totalWeight * marketShare * 100)));
        if (headlineSignal != null) weightDescriptions.add(totalWeight > 0 ? "新闻标题关键词 12.0%" : "新闻标题关键词 100.0%");
        String scoreExplanation = weightDescriptions.isEmpty()
                ? "无可用信号，50/100仅为中性占位，不表示已完成评估。"
                : "启发式代理评分（非收益概率）；可用信号归一化：" + String.join("、", weightDescriptions)
                    + "。PE分档未结合行业或历史分位，不据此认定低估。";

        // ---- 趋势判断 ----
        String trend;
        if (weightDescriptions.isEmpty()) trend = "数据不足";
        else if (sentimentScore >= 0.65)      trend = "稳中向好";
        else if (sentimentScore >= 0.50) trend = "震荡观望";
        else if (sentimentScore >= 0.35) trend = "谨慎悲观";
        else                             trend = "明显看空";

        // ---- 正面因素（条件触发）----
        List<String> positiveFactors = new ArrayList<>();
        if (changePct != null && changePct > 2.0)       positiveFactors.add("股价强势上涨，市场情绪明显积极");
        else if (changePct != null && changePct > 0.5)  positiveFactors.add("股价小幅上涨，多方力量占优");
        if (pe != null && pe > 0 && pe < 35) positiveFactors.add("正PE处于启发式较低分档，尚无行业及历史分位验证");
        if (turnoverRate != null && turnoverRate >= 1.0 && turnoverRate <= 3.0) positiveFactors.add("成交活跃，资金关注度较高");
        if (volumeSignal != null && volumeSignal > 0.60)   positiveFactors.add("近期量能温和放大，上攻意愿增强");
        if (headlineSignal != null && headlineSignal.positiveHits() > 0)
            positiveFactors.add(String.format("近期 %d 篇资讯命中利好关键词（%s）",
                    headlineSignal.positiveHits(), String.join("、", headlineSignal.positiveKeywords())));
        if (positiveFactors.isEmpty()) positiveFactors.add("现有可用信号未触发明确正面因素，不代表风险已排除");

        // ---- 负面因素（条件触发）----
        List<String> negativeFactors = new ArrayList<>();
        if (changePct != null && changePct < -2.0)      negativeFactors.add("近期走势偏弱，空方力量占优");
        else if (changePct != null && changePct < -0.5) negativeFactors.add("股价小幅回调，短期情绪偏谨慎");
        if (pe != null && pe < 0) negativeFactors.add("PE为负值，不适用正PE估值比较；需核对盈利口径");
        if (pe != null && pe > 50)               negativeFactors.add("PE进入启发式较高分档，需结合行业及盈利质量复核");
        if (turnoverRate != null && turnoverRate > 4.0)    negativeFactors.add("换手率过高，存在筹码松动风险");
        if (volumeSignal != null && volumeSignal < 0.42)   negativeFactors.add("成交量萎缩，市场关注度下降");
        if (headlineSignal != null && headlineSignal.negativeHits() > 0)
            negativeFactors.add(String.format("近期 %d 篇资讯命中利空关键词（%s）",
                    headlineSignal.negativeHits(), String.join("、", headlineSignal.negativeKeywords())));
        negativeFactors.add("宏观经济不确定性仍存，需关注外部风险");
        if (negativeFactors.size() == 1 && sentimentScore > 0.60) {
            // 仅有兜底一条时，情绪好的情况下去掉，避免过于负面
            negativeFactors.clear();
            negativeFactors.add("需持续关注宏观政策及行业竞争变化");
        }

        // ---- 新闻数据（真实新闻源；不可用时保持为 null，绝不按市值编造数量）----
        Map<String, Integer> newsCountBySource = null;
        List<String> recentHeadlines = null;
        if (news.isPresent()) {
            NewsResolution resolution = news.get();
            newsCountBySource = new LinkedHashMap<>();
            newsCountBySource.put(resolution.snapshot().sourceName(), resolution.snapshot().articles().size());
            recentHeadlines = resolution.snapshot().articles().stream()
                    .map(article -> article.title())
                    .filter(title -> title != null && !title.isBlank())
                    .limit(NEWS_HEADLINE_LIMIT)
                    .toList();
            if (recentHeadlines.isEmpty()) {
                recentHeadlines = null;
            }
        }

        // ---- 摘要 ----
        String newsSuffix = newsCountBySource == null
                ? "新闻源暂不可用，舆情判断基于行情信号。"
                : String.format("新闻源近期收录相关资讯 %d 篇。", newsCountBySource.values().iterator().next());
        String peSummary = valuationStatus.equals("MISSING") ? "PE未提供或无效，未据此推断亏损"
                : valuationStatus.equals("NON_POSITIVE") ? "PE为负值，需核对盈利口径"
                : String.format(java.util.Locale.ROOT, "当前PE %.1f倍，未验证行业及历史分位", pe);
        String analysisSummary = String.format(java.util.Locale.ROOT,
                "行情与新闻代理评分%.0f/100，趋势%s。%s。%s%s", sentimentScore * 100, trend,
                peSummary, newsSuffix, scoreExplanation);

        return SentimentDataDTO.builder()
                .sentimentScore(sentimentScore)
                .sentimentTrend(trend)
                .positiveFactors(positiveFactors)
                .negativeFactors(negativeFactors)
                .newsCountBySource(newsCountBySource)
                .mediaAttention(null)
                .valuationStatus(valuationStatus)
                .scoreExplanation(scoreExplanation)
                .signalCoverage(Math.round((totalWeight * 0.88 + (headlineSignal == null ? 0 : 0.12)) * 100) / 100.0)
                .dataGaps(dataGaps)
                .analysisSummary(analysisSummary)
                .recentHeadlines(recentHeadlines)
                .newsSignal(headlineSignal != null ? headlineSignal.score() : null)
                .newsDataSource(news.map(resolution -> resolution.snapshot().sourceName()).orElse(null))
                .newsStale(news.map(NewsResolution::stale).orElse(null))
                .build();
    }

    private static String scoreExplanation(SentimentDataDTO data) {
        return data.getScoreExplanation() == null ? "旧报告未记录评分口径" : data.getScoreExplanation();
    }

    private static String dataGapDescription(SentimentDataDTO data) {
        return data.getDataGaps() == null ? "旧报告未记录" : String.join("；", data.getDataGaps());
    }

    private static Double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }
}
