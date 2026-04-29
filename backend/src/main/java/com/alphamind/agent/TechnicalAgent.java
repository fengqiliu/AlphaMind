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
            5. 结合多个指标给出综合技术评分（70+看多，40-70中性，40以下看空）

            回答要求：
            - 用中文专业简洁地分析各指标
            - 明确指出当前最重要的技术信号
            - 给出综合技术评分
            """;
    }

    private String generateTechnicalInterpretation(TechnicalIndicatorsDTO ind, MarketDataDTO market) {
        String prompt = String.format(
                "请简要解读以下技术指标：MACD DIF=%.2f DEA=%.2f 柱=%.2f；RSI(12)=%.1f；KDJ K=%.1f D=%.1f J=%.1f；技术评分=%d/100",
                ind.getMacd().getDif(), ind.getMacd().getDea(), ind.getMacd().getHistogram(),
                ind.getRsi().getRsi12(), ind.getKdj().getK(), ind.getKdj().getD(), ind.getKdj().getJ(),
                ind.getTechnicalScore());
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : (ind.getTechnicalScore() >= 70 ? "技术面偏多，多指标共振向上" :
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
                ind.getMacd().getHistogram() > 0 ? "多头" : "空头",
                ind.getRsi().getRsi6(), ind.getRsi().getRsi12(), ind.getRsi().getRsi24(),
                ind.getKdj().getK(), ind.getKdj().getD(), ind.getKdj().getJ(),
                ind.getBollinger().getUpper(), ind.getBollinger().getMiddle(), ind.getBollinger().getLower(),
                ind.getTechnicalScore(), question);
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
                macd.getHistogram() > 0 ? "红柱(多头)" : "绿柱(空头)",
                rsi.getRsi6(), rsi.getRsi12(), rsi.getRsi24(),
                rsi.getRsi12() > 70 ? "超买区域" : rsi.getRsi12() < 30 ? "超卖区域" : "正常区间",
                kdj.getK(), kdj.getD(), kdj.getJ(),
                bb.getUpper(), bb.getMiddle(), bb.getLower(),
                ind.getTechnicalScore(),
                ind.getTechnicalScore() >= 70 ? "✅ 技术面偏多" :
                        ind.getTechnicalScore() >= 40 ? "⚖️ 技术面中性" : "❌ 技术面偏空");
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
     * 从 MarketAgent 提供的 K 线数据计算技术指标
     * MACD: EMA12/EMA26 → DIF, EMA9(DIF) → DEA, 柱 = (DIF-DEA)*2
     * RSI:  Wilder 平滑法 RSI(6/12/24)
     * KDJ:  9 日最高/最低 → RSV → K/D/J
     * 布林带: MA20 ± 2σ
     */
    private TechnicalIndicatorsDTO calculateTechnicalIndicators(MarketDataDTO marketData) {
        java.util.List<double[]> klines = marketData.getKlines();
        double price = marketData.getCurrentPrice();

        // 从 klines 提取 close / high / low（格式：[open, close, low, high]）
        double[] closes, highs, lows;
        if (klines != null && klines.size() >= 26) {
            int n = klines.size();
            closes = new double[n];
            highs  = new double[n];
            lows   = new double[n];
            for (int i = 0; i < n; i++) {
                closes[i] = klines.get(i)[1];
                lows[i]   = klines.get(i)[2];
                highs[i]  = klines.get(i)[3];
            }
        } else {
            // K 线数据不足时构造近似序列（兜底）
            closes = new double[60];
            highs  = new double[60];
            lows   = new double[60];
            double cp = price;
            for (int i = 59; i >= 0; i--) {
                closes[i] = cp;
                highs[i]  = cp * 1.01;
                lows[i]   = cp * 0.99;
                cp /= 1.001;
            }
        }

        // ── MACD (EMA12 / EMA26 / EMA9) ──────────────
        double[] ema12 = ema(closes, 12);
        double[] ema26 = ema(closes, 26);
        int last = closes.length - 1;
        double[] difArr = new double[closes.length];
        for (int i = 0; i < closes.length; i++) difArr[i] = ema12[i] - ema26[i];
        double[] deaArr = ema(difArr, 9);
        double difVal  = r3(difArr[last]);
        double deaVal  = r3(deaArr[last]);
        double histVal = r3((difVal - deaVal) * 2);

        // ── RSI (Wilder) ──────────────────────────────
        double rsi6  = r2(rsi(closes, 6));
        double rsi12 = r2(rsi(closes, 12));
        double rsi24 = r2(rsi(closes, 24));

        // ── KDJ (9-period Stochastic) ─────────────────
        double[] kdjArr = kdj(closes, highs, lows, 9);
        double kVal = r2(kdjArr[0]);
        double dVal = r2(kdjArr[1]);
        double jVal = r2(kdjArr[2]);

        // ── Bollinger Bands (MA20 ± 2σ) ──────────────
        double[] boll = bollinger(closes, 20);
        double bolUpper  = r2(boll[0]);
        double bolMiddle = r2(boll[1]);
        double bolLower  = r2(boll[2]);

        // ── 综合技术评分 ───────────────────────────────
        int score = 50;
        if (histVal > 0)           score += 15; else score -= 10;
        if (rsi12 > 40 && rsi12 < 70) score += 10;
        if (rsi12 < 30)            score += 15;   // 超卖反弹机会
        if (rsi12 > 70)            score -= 10;   // 超买风险
        if (jVal  < 20)            score += 10;   // KDJ 超卖
        if (jVal  > 90)            score -= 8;    // KDJ 超买
        if (price < bolLower)      score += 8;    // 价格跌破下轨
        if (price > bolUpper)      score -= 8;    // 价格突破上轨
        score = Math.max(10, Math.min(95, score));

        return TechnicalIndicatorsDTO.builder()
                .macd(TechnicalIndicatorsDTO.MacdDTO.builder()
                        .dif(difVal).dea(deaVal).histogram(histVal).build())
                .rsi(TechnicalIndicatorsDTO.RsiDTO.builder()
                        .rsi6(rsi6).rsi12(rsi12).rsi24(rsi24).build())
                .kdj(TechnicalIndicatorsDTO.KdjDTO.builder()
                        .k(kVal).d(dVal).j(jVal).build())
                .bollinger(TechnicalIndicatorsDTO.BollingerDTO.builder()
                        .upper(bolUpper).middle(bolMiddle).lower(bolLower).build())
                .technicalScore(score)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 技术指标计算工具方法
    // ─────────────────────────────────────────────────────────

    /** 指数移动平均 (EMA)：初始值取前 period 个收盘价的 SMA */
    private double[] ema(double[] data, int period) {
        double[] result = new double[data.length];
        double mult = 2.0 / (period + 1);
        int warmup = Math.min(period - 1, data.length - 1);
        double sum = 0;
        for (int i = 0; i <= warmup; i++) sum += data[i];
        result[warmup] = sum / (warmup + 1);
        for (int i = warmup + 1; i < data.length; i++) {
            result[i] = data[i] * mult + result[i - 1] * (1 - mult);
        }
        return result;
    }

    /** Wilder 平滑法 RSI */
    private double rsi(double[] closes, int period) {
        if (closes.length < period + 1) return 50.0;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff > 0) avgGain += diff; else avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < closes.length; i++) {
            double diff = closes[i] - closes[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
        }
        if (avgLoss == 0) return 100.0;
        return 100.0 - 100.0 / (1 + avgGain / avgLoss);
    }

    /** 9 日随机指标 KDJ */
    private double[] kdj(double[] closes, double[] highs, double[] lows, int period) {
        double k = 50, d = 50;
        for (int i = period - 1; i < closes.length; i++) {
            double hh = highs[i], ll = lows[i];
            for (int j = i - period + 1; j < i; j++) {
                hh = Math.max(hh, highs[j]);
                ll = Math.min(ll, lows[j]);
            }
            double rsv = (hh == ll) ? 50 : (closes[i] - ll) / (hh - ll) * 100;
            k = 2.0 / 3.0 * k + 1.0 / 3.0 * rsv;
            d = 2.0 / 3.0 * d + 1.0 / 3.0 * k;
        }
        return new double[]{k, d, 3 * k - 2 * d};
    }

    /** MA(period) 布林带，返回 [upper, middle, lower] */
    private double[] bollinger(double[] closes, int period) {
        int n = closes.length;
        if (n < period) period = n;
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += closes[i];
        double mean = sum / period;
        double var = 0;
        for (int i = n - period; i < n; i++) {
            double diff = closes[i] - mean;
            var += diff * diff;
        }
        double sigma = Math.sqrt(var / period);
        return new double[]{mean + 2 * sigma, mean, mean - 2 * sigma};
    }

    private double r2(double v) { return Math.round(v * 100) / 100.0; }
    private double r3(double v) { return Math.round(v * 1000) / 1000.0; }
}
