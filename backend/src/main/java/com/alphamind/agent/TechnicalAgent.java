package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.alphamind.scoring.TechnicalScoring;
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
            if (marketData.getCurrentPrice() == null) throw new IllegalArgumentException("缺少有效报价");
            TechnicalIndicatorsDTO indicators = TechnicalScoring.score(marketData.getKlines(), marketData.getCurrentPrice());

            // 调用LLM生成技术分析解读
            String aiInterpretation = generateTechnicalInterpretation(indicators, marketData);
            indicators.setAiInterpretation(aiInterpretation);

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
        if (indicators == null) {
            return buildMsg("暂无技术分析数据，请先发起分析。");
        }

        // 优先使用LLM生成回复
        String response = llmCall(getSystemPrompt(), buildTechPrompt(indicators, userMessage.getContent()));
        if (response == null) {
            response = buildTechTemplateResponse(indicators);
        }

        return buildMsg(response);
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
            5. 结合多个指标给出综合技术评分（>=70偏多，[40,70)中性，<40偏空；启发式而非概率）

            回答要求：
            - 用中文专业简洁地分析各指标
            - 明确指出当前最重要的技术信号
            - 给出综合技术评分
            - 单点指标不证明金叉、死叉、背离或持续变化，不凭缺失数据推测这些事实
            """;
    }

    private String generateTechnicalInterpretation(TechnicalIndicatorsDTO ind, MarketDataDTO market) {
        String prompt = String.format(
                "请简要解读以下技术指标：MACD DIF=%.2f DEA=%.2f 柱=%.2f；RSI(12)=%.1f；KDJ K=%.1f D=%.1f J=%.1f；技术评分=%d/100",
                ind.getMacd().getDif(), ind.getMacd().getDea(), ind.getMacd().getHistogram(),
                ind.getRsi().getRsi12(), ind.getKdj().getK(), ind.getKdj().getD(), ind.getKdj().getJ(),
                ind.getTechnicalScore()) + "。评分依据：" + ind.getScoreExplanation();
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : ind.getScoreExplanation() + (ind.getTechnicalScore() >= 70 ? "技术面偏多，按规则得分偏多" :
                ind.getTechnicalScore() >= 40 ? "技术面中性，方向有待确认" : "技术面偏空，需警惕下行风险");
    }

    private String buildTechPrompt(TechnicalIndicatorsDTO ind, String question) {
        return String.format("""
                技术指标数据：
                - MACD: DIF=%.3f, DEA=%.3f, 柱=%.3f (%s)
                - RSI: (6)=%.1f (12)=%.1f (24)=%.1f
                - KDJ: K=%.1f D=%.1f J=%.1f
                - 布林带: 上=%.2f 中=%.2f 下=%.2f
                - 综合技术评分: %d/100

                用户问题：%s
                """,
                ind.getMacd().getDif(), ind.getMacd().getDea(), ind.getMacd().getHistogram(),
                ind.getMacd().getHistogram() > 0 ? "正值" : ind.getMacd().getHistogram() < 0 ? "负值" : "零值",
                ind.getRsi().getRsi6(), ind.getRsi().getRsi12(), ind.getRsi().getRsi24(),
                ind.getKdj().getK(), ind.getKdj().getD(), ind.getKdj().getJ(),
                ind.getBollinger().getUpper(), ind.getBollinger().getMiddle(), ind.getBollinger().getLower(),
                ind.getTechnicalScore(), question) + "\n评分口径：" + ind.getScoreExplanation();
    }

    private String buildTechTemplateResponse(TechnicalIndicatorsDTO ind) {
        TechnicalIndicatorsDTO.MacdDTO macd = ind.getMacd();
        TechnicalIndicatorsDTO.RsiDTO rsi = ind.getRsi();
        TechnicalIndicatorsDTO.KdjDTO kdj = ind.getKdj();
        TechnicalIndicatorsDTO.BollingerDTO bb = ind.getBollinger();

        return String.format("""
                **技术指标分析报告**

                **MACD指标**
                - DIF: %.3f | DEA: %.3f | 柱: %.3f（%s）

                **RSI指标**
                - RSI(6): %.2f | RSI(12): %.2f | RSI(24): %.2f
                - 当前状态: %s

                **KDJ指标**
                - K: %.2f | D: %.2f | J: %.2f

                **布林带**
                - 上轨: %.2f | 中轨: %.2f | 下轨: %.2f

                **综合技术评分**: %d/100  %s
                """,
                macd.getDif(), macd.getDea(), macd.getHistogram(),
                macd.getHistogram() > 0 ? "正值" : macd.getHistogram() < 0 ? "负值" : "零值",
                rsi.getRsi6(), rsi.getRsi12(), rsi.getRsi24(),
                rsi.getRsi12() > 70 ? "超买区域" : rsi.getRsi12() < 30 ? "超卖区域" : "正常区间",
                kdj.getK(), kdj.getD(), kdj.getJ(),
                bb.getUpper(), bb.getMiddle(), bb.getLower(),
                ind.getTechnicalScore(),
                ind.getTechnicalScore() >= 70 ? "✅ 技术面偏多" :
                        ind.getTechnicalScore() >= 40 ? "⚖️ 技术面中性" : "❌ 技术面偏空") + "\n评分口径：" + ind.getScoreExplanation();
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
}
