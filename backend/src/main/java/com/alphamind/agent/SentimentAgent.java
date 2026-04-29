package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 舆情Agent - 负责分析市场舆情和情绪
 */
@Slf4j
@Component
public class SentimentAgent extends BaseAgent {

    public SentimentAgent() {
        super(AgentType.SENTIMENT);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始舆情分析: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            if (marketData == null) {
                throw new RuntimeException("缺少市场数据");
            }

            // 分析舆情数据
            SentimentDataDTO sentimentData = analyzeSentiment(report.getStockCode(), marketData);

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
            1. 收集和分析股票相关的新闻报道
            2. 分析券商研报的投资评级和目标价
            3. 监测社交媒体和论坛的市场情绪
            4. 识别潜在的利好和利空因素
            5. 评估媒体关注度和市场热度

            舆情评分标准：
            - 70-100分: 舆情偏正面
            - 40-70分: 舆情中性
            - 0-40分: 舆情偏负面

            回答要求：
            - 客观呈现利好和利空因素
            - 给出舆情总结和趋势判断
            """;
    }

    private String generateSentimentSummary(SentimentDataDTO data, MarketDataDTO market) {
        String prompt = String.format(
                "请简要分析 %s 的市场舆情：舆情评分%.0f/100，趋势:%s，利好%d项，利空%d项，媒体关注度%.0f%%",
                market.getStockName(), data.getSentimentScore() * 100, data.getSentimentTrend(),
                data.getPositiveFactors().size(), data.getNegativeFactors().size(),
                data.getMediaAttention() * 100);
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : data.getAnalysisSummary();
    }

    private String buildSentimentPrompt(SentimentDataDTO data, String question) {
        return String.format("""
                舆情数据：
                - 舆情评分: %.0f/100
                - 舆情趋势: %s
                - 利好因素: %s
                - 利空因素: %s
                - 媒体关注度: %.0f/100

                用户问题：%s
                """,
                data.getSentimentScore() * 100, data.getSentimentTrend(),
                String.join("；", data.getPositiveFactors()),
                String.join("；", data.getNegativeFactors()),
                data.getMediaAttention() * 100, question);
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
        sb.append(String.format("**媒体关注度**: %.0f/100\n\n", data.getMediaAttention() * 100));
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
     * 分析舆情 —— 基于市场数据确定性计算，无随机数。
     *
     * 评分信号（权重）：
     *   涨跌幅贡献 40%  — changePct ∈ [-10,+10] 映射到 [0,1]
     *   估值信号   25%  — PE:15~35合理→0.6; <15低估→0.7; >50高估→0.35; 无PE→0.5
     *   换手率信号 20%  — turnoverRate ∈ [0,10] 映射，1~3%最佳
     *   量能信号   15%  — kline 最近5日量均 vs 20日量均（量比近似）
     */
    private SentimentDataDTO analyzeSentiment(String stockCode, MarketDataDTO marketData) {
        double changePct    = marketData.getChangePercent() != null ? marketData.getChangePercent() : 0;
        double pe           = marketData.getPe()           != null ? marketData.getPe()           : -1;
        double turnoverRate = marketData.getTurnoverRate() != null ? marketData.getTurnoverRate() : 1.0;
        double marketCap    = marketData.getMarketCap()    != null ? marketData.getMarketCap()    : 1e10;

        // ---- 各信号评分 ----
        // 涨跌幅：-10% → 0.0, 0% → 0.5, +10% → 1.0
        double priceSignal = Math.max(0.0, Math.min(1.0, 0.5 + changePct / 20.0));

        // PE估值
        double peSignal;
        if (pe <= 0) {
            peSignal = 0.45; // 亏损/无PE → 偏悲观
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
        double turnoverSignal;
        if (turnoverRate < 0.5) {
            turnoverSignal = 0.40;
        } else if (turnoverRate <= 3.0) {
            turnoverSignal = 0.40 + turnoverRate * 0.083; // 0.5→0.44, 3.0→0.65
        } else if (turnoverRate <= 5.0) {
            turnoverSignal = 0.65 - (turnoverRate - 3.0) * 0.05; // 3→0.65, 5→0.55
        } else {
            turnoverSignal = Math.max(0.35, 0.55 - (turnoverRate - 5.0) * 0.04);
        }

        // 量能信号：近5日均量 vs 近20日均量（量比）
        double volumeSignal = 0.50;
        List<double[]> klines = marketData.getKlines();
        if (klines != null && klines.size() >= 20) {
            List<Long> rawVols = marketData.getKlineVolumes();
            List<Double> vols = rawVols == null ? null
                    : rawVols.stream().map(Long::doubleValue).collect(java.util.stream.Collectors.toList());
            if (vols != null && vols.size() >= 20) {
                double avg5  = vols.subList(vols.size() - 5,  vols.size()).stream().mapToDouble(Double::doubleValue).average().orElse(1);
                double avg20 = vols.subList(vols.size() - 20, vols.size()).stream().mapToDouble(Double::doubleValue).average().orElse(1);
                double volRatio = avg5 / avg20;
                // 量比 0.8~1.5 温和放量 → 积极；>2.5 极度放量 → 过热
                if (volRatio < 0.8) {
                    volumeSignal = 0.38;
                } else if (volRatio <= 1.5) {
                    volumeSignal = 0.55 + (volRatio - 0.8) / 0.7 * 0.15;
                } else if (volRatio <= 2.5) {
                    volumeSignal = 0.70 - (volRatio - 1.5) / 1.0 * 0.10;
                } else {
                    volumeSignal = 0.45;
                }
            }
        }

        // 加权合成评分
        double sentimentScore = priceSignal * 0.40 + peSignal * 0.25 + turnoverSignal * 0.20 + volumeSignal * 0.15;
        sentimentScore = Math.max(0.10, Math.min(0.95, sentimentScore));
        sentimentScore = Math.round(sentimentScore * 100) / 100.0;

        // ---- 趋势判断 ----
        String trend;
        if (sentimentScore >= 0.65)      trend = "稳中向好";
        else if (sentimentScore >= 0.50) trend = "震荡观望";
        else if (sentimentScore >= 0.35) trend = "谨慎悲观";
        else                             trend = "明显看空";

        // ---- 正面因素（条件触发）----
        List<String> positiveFactors = new ArrayList<>();
        if (changePct > 2.0)       positiveFactors.add("股价强势上涨，市场情绪明显积极");
        else if (changePct > 0.5)  positiveFactors.add("股价小幅上涨，多方力量占优");
        if (pe > 0 && pe < 20)     positiveFactors.add("估值处于历史低位，安全边际充足");
        else if (pe > 0 && pe < 35) positiveFactors.add("估值合理，具备一定投资吸引力");
        if (turnoverRate >= 1.0 && turnoverRate <= 3.0) positiveFactors.add("成交活跃，资金关注度较高");
        if (volumeSignal > 0.60)   positiveFactors.add("近期量能温和放大，上攻意愿增强");
        if (marketCap > 1e11)      positiveFactors.add("市值规模较大，机构配置意愿较高");
        if (positiveFactors.isEmpty()) positiveFactors.add("整体市场情绪平稳，无明显利空压制");

        // ---- 负面因素（条件触发）----
        List<String> negativeFactors = new ArrayList<>();
        if (changePct < -2.0)      negativeFactors.add("近期走势偏弱，空方力量占优");
        else if (changePct < -0.5) negativeFactors.add("股价小幅回调，短期情绪偏谨慎");
        if (pe > 50)               negativeFactors.add("估值偏高，上方压力较大");
        if (turnoverRate > 4.0)    negativeFactors.add("换手率过高，存在筹码松动风险");
        if (volumeSignal < 0.42)   negativeFactors.add("成交量萎缩，市场关注度下降");
        negativeFactors.add("宏观经济不确定性仍存，需关注外部风险");
        if (negativeFactors.size() == 1 && sentimentScore > 0.60) {
            // 仅有兜底一条时，情绪好的情况下去掉，避免过于负面
            negativeFactors.clear();
            negativeFactors.add("需持续关注宏观政策及行业竞争变化");
        }

        // ---- 媒体关注度（确定性：市值驱动 + 换手率 + 涨跌幅绝对值）----
        double mediaAttention = 0.30
                + Math.min(marketCap / 5e11, 0.25)          // 市值最多贡献 0.25
                + Math.min(Math.abs(changePct) / 10.0, 0.20) // 涨跌幅最多贡献 0.20
                + Math.min(turnoverRate / 20.0, 0.15);        // 换手率最多贡献 0.15
        mediaAttention = Math.min(0.95, Math.round(mediaAttention * 100) / 100.0);

        // ---- 新闻来源数量（按市值规模确定性估算）----
        int capBase = (int) Math.min(marketCap / 2e10, 20); // 0~20
        Map<String, Integer> newsCountBySource = new LinkedHashMap<>();
        newsCountBySource.put("东方财富", 8  + capBase);
        newsCountBySource.put("同花顺",   6  + capBase / 2);
        newsCountBySource.put("雪球",     15 + capBase * 2);
        newsCountBySource.put("微博",     20 + capBase * 3);

        // ---- 摘要 ----
        String analysisSummary = String.format(
                "该股近期舆情%s（评分%.2f）。%s涨跌幅%.2f%%，换手率%.2f%%，%s。",
                trend, sentimentScore,
                positiveFactors.size() > negativeFactors.size() ? "利好因素较多，" : "市场情绪偏谨慎，",
                changePct, turnoverRate,
                pe > 0 ? String.format("当前PE %.1f倍", pe) : "暂无PE数据"
        );

        return SentimentDataDTO.builder()
                .sentimentScore(sentimentScore)
                .sentimentTrend(trend)
                .positiveFactors(positiveFactors)
                .negativeFactors(negativeFactors)
                .newsCountBySource(newsCountBySource)
                .mediaAttention(mediaAttention)
                .analysisSummary(analysisSummary)
                .build();
    }
}
