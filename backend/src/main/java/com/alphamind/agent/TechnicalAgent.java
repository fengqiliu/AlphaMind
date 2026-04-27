package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技术Agent - 负责技术指标分析和图表模式识别
 */
@Slf4j
@Component
public class TechnicalAgent extends BaseAgent {

    public TechnicalAgent() {
        super(AgentType.TECHNICAL);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始技术分析: " + report.getStockCode());

        try {
            MarketDataDTO marketData = getContext("marketData");
            if (marketData == null) {
                throw new RuntimeException("缺少市场数据");
            }

            // 计算技术指标
            TechnicalIndicatorsDTO indicators = calculateTechnicalIndicators(marketData);

            report.setTechnicalIndicators(indicators);
            setContext("technicalIndicators", indicators);

            logInfo("技术分析完成，综合评分: " + indicators.getTechnicalScore());
            return report;
        } catch (Exception e) {
            logError("技术分析失败", e);
            throw new RuntimeException("技术分析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        TechnicalIndicatorsDTO indicators = getContext("technicalIndicators");

        StringBuilder response = new StringBuilder();
        response.append("**技术指标分析报告**\n\n");

        // MACD分析
        TechnicalIndicatorsDTO.MacdDTO macd = indicators.getMacd();
        response.append("**MACD指标**\n");
        response.append(String.format("- DIF: %.3f\n", macd.getDif()));
        response.append(String.format("- DEA: %.3f\n", macd.getDea()));
        response.append(String.format("- MACD柱: %.3f (%s)\n",
                macd.getHistogram(),
                macd.getHistogram() > 0 ? "红柱(多头)" : "绿柱(空头)"));
        response.append("\n");

        // RSI分析
        TechnicalIndicatorsDTO.RsiDTO rsi = indicators.getRsi();
        response.append("**RSI指标**\n");
        response.append(String.format("- RSI(6): %.2f\n", rsi.getRsi6()));
        response.append(String.format("- RSI(12): %.2f\n", rsi.getRsi12()));
        response.append(String.format("- RSI(24): %.2f\n", rsi.getRsi24()));
        response.append(String.format("- 当前状态: %s\n\n",
                rsi.getRsi12() > 70 ? "超买区域" : rsi.getRsi12() < 30 ? "超卖区域" : "正常区间"));

        // KDJ分析
        TechnicalIndicatorsDTO.KdjDTO kdj = indicators.getKdj();
        response.append("**KDJ指标**\n");
        response.append(String.format("- K值: %.2f\n", kdj.getK()));
        response.append(String.format("- D值: %.2f\n", kdj.getD()));
        response.append(String.format("- J值: %.2f\n\n", kdj.getJ()));

        // 布林带
        TechnicalIndicatorsDTO.BollingerDTO bollinger = indicators.getBollinger();
        response.append("**布林带**\n");
        response.append(String.format("- 上轨: %.2f\n", bollinger.getUpper()));
        response.append(String.format("- 中轨: %.2f\n", bollinger.getMiddle()));
        response.append(String.format("- 下轨: %.2f\n\n", bollinger.getLower()));

        // 综合评分
        response.append("**综合技术评分**: ").append(indicators.getTechnicalScore()).append("/100\n");
        response.append(indicators.getTechnicalScore() >= 70 ? "✅ 技术面偏多" :
                indicators.getTechnicalScore() >= 40 ? "⚖️ 技术面中性" : "❌ 技术面偏空");

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response.toString())
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(getModelName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名专业的技术分析师，负责分析股票的技术指标和图表形态。

            你的职责：
            1. 分析MACD指标，判断金叉/死叉、背离等信号
            2. 分析RSI指标，判断超买/超卖状态
            3. 分析KDJ指标，判断短期趋势
            4. 分析布林带，判断价格波动区间
            5. 结合多个指标给出综合技术评分

            技术评分标准：
            - 70-100分: 强烈看多
            - 40-70分: 中性偏多/偏空
            - 0-40分: 强烈看空

            回答要求：
            - 详细解释每个指标的含义
            - 指出潜在的交易信号
            - 给出综合技术评分
            """;
    }

    /**
     * 计算技术指标 (模拟实现)
     * TODO: 接入真实历史数据计算
     */
    private TechnicalIndicatorsDTO calculateTechnicalIndicators(MarketDataDTO marketData) {
        double price = marketData.getCurrentPrice();

        return TechnicalIndicatorsDTO.builder()
                .macd(TechnicalIndicatorsDTO.MacdDTO.builder()
                        .dif(2.5)
                        .dea(1.8)
                        .histogram(0.7)
                        .build())
                .rsi(TechnicalIndicatorsDTO.RsiDTO.builder()
                        .rsi6(72.5)
                        .rsi12(68.3)
                        .rsi24(65.8)
                        .build())
                .kdj(TechnicalIndicatorsDTO.KdjDTO.builder()
                        .k(75.2)
                        .d(70.8)
                        .j(84.0)
                        .build())
                .bollinger(TechnicalIndicatorsDTO.BollingerDTO.builder()
                        .upper(price * 1.08)
                        .middle(price)
                        .lower(price * 0.92)
                        .build())
                .technicalScore(68)
                .build();
    }
}
