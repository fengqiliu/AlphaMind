package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 行情Agent - 负责采集和处理股票行情数据
 */
@Slf4j
@Component
public class MarketAgent extends BaseAgent {

    // 模拟股票数据库（TODO: 接入 Tushare/AKShare 真实数据源）
    private static final Map<String, double[]> STOCK_DATA = Map.ofEntries(
            Map.entry("600519", new double[]{1680.50, 25.30, 1.53, 1655.20, 1692.00, 1648.50, 35.8, 12.5, 2110e9, 0.26, 3250000L}),
            Map.entry("000858", new double[]{165.30,  1.35,  0.82, 163.90, 166.80, 162.50, 28.5,  7.2, 640e9,  1.25, 52000000L}),
            Map.entry("000001", new double[]{12.35,  -0.05, -0.40, 12.40,  12.52,  12.28, 6.5,   0.8, 239e9,  0.45, 180000000L}),
            Map.entry("600036", new double[]{35.60,   0.44,  1.25, 35.20,  35.88,  35.05, 9.8,   1.5, 895e9,  0.38, 120000000L}),
            Map.entry("601318", new double[]{48.25,   0.33,  0.68, 47.90,  48.55,  47.70, 10.2,  1.8, 878e9,  0.32, 96000000L}),
            Map.entry("000333", new double[]{62.80,  -0.22, -0.35, 63.00,  63.45,  62.50, 18.5,  3.5, 440e9,  0.55, 72000000L}),
            Map.entry("002594", new double[]{238.50,  5.02,  2.15, 233.50, 241.80, 232.00, 55.0, 11.2, 690e9,  1.85, 88000000L}),
            Map.entry("600276", new double[]{52.30,  -0.64, -1.20, 52.90,  53.10,  52.00, 42.5,  5.8, 525e9,  0.72, 45000000L}),
            Map.entry("300750", new double[]{185.60,  5.82,  3.25, 180.00, 188.50, 179.50, 35.2,  8.5, 815e9,  2.25, 110000000L}),
            Map.entry("688981", new double[]{52.80,  -1.28, -2.35, 54.00,  54.20,  52.50, 120.5, 4.2, 430e9,  3.15, 95000000L})
    );

    private static final Map<String, String> STOCK_NAMES = Map.ofEntries(
            Map.entry("600519", "贵州茅台"), Map.entry("000858", "五粮液"),
            Map.entry("000001", "平安银行"), Map.entry("600036", "招商银行"),
            Map.entry("601318", "中国平安"), Map.entry("000333", "美的集团"),
            Map.entry("002594", "比亚迪"),   Map.entry("600276", "恒瑞医药"),
            Map.entry("300750", "宁德时代"), Map.entry("688981", "中芯国际")
    );

    public MarketAgent() {
        super(AgentType.MARKET);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始采集行情数据: " + report.getStockCode());

        try {
            MarketDataDTO marketData = fetchMarketData(report.getStockCode(), report.getStockName());

            // 调用LLM生成行情分析摘要
            String aiSummary = generateMarketSummary(marketData);
            marketData.setAiSummary(aiSummary);

            report.setMarketData(marketData);
            setContext("marketData", marketData);

            logInfo("行情数据采集完成，当前价格: " + marketData.getCurrentPrice());
            return report;
        } catch (Exception e) {
            logError("行情数据采集失败", e);
            throw new RuntimeException("行情数据采集失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        if (marketData == null) {
            return buildErrorMessage("暂无行情数据，请先发起分析。");
        }

        // 优先使用LLM生成回复
        String response = llmCall(getSystemPrompt(),
                buildMarketPrompt(marketData, userMessage.getContent()));

        if (response == null) {
            // 降级为模板响应
            response = buildMarketTemplateResponse(marketData);
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
            你是一名专业的股票行情分析师，负责采集和解读股票市场的实时行情数据。

            你的职责：
            1. 解读股票的实时价格、涨跌幅、成交量等基础数据
            2. 分析开盘价、最高价、最低价的关系判断当日走势
            3. 解读市盈率、市净率等估值指标
            4. 结合市值和换手率判断市场活跃度
            5. 用简洁专业的语言向用户解释行情数据

            回答要求：
            - 客观准确，给出数据分析结论
            - 数据异常时需要特别标注
            - 涉及专业术语时简要解释
            """;
    }

    private String generateMarketSummary(MarketDataDTO data) {
        String prompt = String.format(
                "请用50字以内简要分析 %s(%s) 的行情：当前价¥%.2f，涨跌%+.2f%%，PE:%.1f，换手率%.2f%%",
                data.getStockName(), data.getStockCode(), data.getCurrentPrice(),
                data.getChangePercent(), data.getPe(), data.getTurnoverRate());
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : buildDefaultSummary(data);
    }

    private String buildDefaultSummary(MarketDataDTO data) {
        return String.format("%s 当前报价¥%.2f，%s%.2f%%。PE %.1f，换手率%.2f%%。",
                data.getStockName(), data.getCurrentPrice(),
                data.getChangePercent() >= 0 ? "上涨" : "下跌",
                Math.abs(data.getChangePercent()),
                data.getPe(), data.getTurnoverRate());
    }

    private String buildMarketPrompt(MarketDataDTO data, String userQuestion) {
        return String.format("""
                股票行情数据：
                - 股票：%s (%s)
                - 当前价：¥%.2f，涨跌：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万手，换手率：%.2f%%
                - 市盈率(PE)：%.2f，市净率(PB)：%.2f
                - 总市值：%.2f亿

                用户问题：%s
                """,
                data.getStockName(), data.getStockCode(),
                data.getCurrentPrice(), data.getChangePercent(), data.getChange(),
                data.getOpen(), data.getHigh(), data.getLow(),
                data.getVolume() / 10000.0, data.getTurnoverRate(),
                data.getPe(), data.getPb(), data.getMarketCap() / 1e8,
                userQuestion);
    }

    private String buildMarketTemplateResponse(MarketDataDTO data) {
        return String.format("""
                **%s (%s) 行情数据**

                - 当前价格：¥%.2f
                - 涨跌幅：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万手 | 换手率：%.2f%%
                - 市盈率(PE)：%.2f | 市净率(PB)：%.2f
                - 总市值：%.2f亿

                数据更新时间：%s
                """,
                data.getStockName(), data.getStockCode(),
                data.getCurrentPrice(), data.getChangePercent(), data.getChange(),
                data.getOpen(), data.getHigh(), data.getLow(),
                data.getVolume() / 10000.0, data.getTurnoverRate(),
                data.getPe(), data.getPb(), data.getMarketCap() / 1e8,
                data.getUpdateTime());
    }

    private ChatMessage buildErrorMessage(String content) {
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .agentType(agentType)
                .agentName(agentType.getName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * 获取市场数据（模拟实现 - TODO: 接入 Tushare Pro 或 AKShare）
     * 根据股票代码返回对应的模拟行情数据，随机加入±2%波动模拟实时性
     */
    private MarketDataDTO fetchMarketData(String stockCode, String stockNameHint) {
        double[] baseData = STOCK_DATA.get(stockCode);
        String stockName = STOCK_NAMES.getOrDefault(stockCode,
                stockNameHint != null ? stockNameHint : stockCode);

        double basePrice, change, changePct, open, high, low, pe, pb, marketCap, turnover;
        long volume;

        if (baseData != null) {
            // 添加轻微随机波动，模拟实时行情
            double jitter = 1 + (Math.random() - 0.5) * 0.02;
            basePrice = baseData[0] * jitter;
            change    = baseData[1] * jitter;
            changePct = baseData[2];
            open      = baseData[3];
            high      = baseData[4] * jitter;
            low       = baseData[5] * jitter;
            pe        = baseData[6];
            pb        = baseData[7];
            marketCap = (long)(baseData[8] * jitter);
            turnover  = baseData[9];
            volume    = (long) baseData[10];
        } else {
            // 未知股票 - 生成合理默认数据
            basePrice = 50.0 + Math.random() * 100;
            change    = (Math.random() - 0.5) * 3;
            changePct = change / basePrice * 100;
            open      = basePrice - change * 0.3;
            high      = basePrice * (1 + Math.random() * 0.03);
            low       = basePrice * (1 - Math.random() * 0.03);
            pe        = 15 + Math.random() * 30;
            pb        = 1 + Math.random() * 5;
            marketCap = (long)(basePrice * (1e8 + Math.random() * 9e9));
            turnover  = 0.5 + Math.random() * 2;
            volume    = (long)(1e7 + Math.random() * 1e8);
        }

        return MarketDataDTO.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(Math.round(basePrice * 100) / 100.0)
                .change(Math.round(change * 100) / 100.0)
                .changePercent(Math.round(changePct * 100) / 100.0)
                .open(Math.round(open * 100) / 100.0)
                .high(Math.round(high * 100) / 100.0)
                .low(Math.round(low * 100) / 100.0)
                .volume(volume)
                .amount(Math.round(basePrice * volume / 1e7) * 1e7)
                .turnoverRate(Math.round(turnover * 100) / 100.0)
                .pe(Math.round(pe * 10) / 10.0)
                .pb(Math.round(pb * 10) / 10.0)
                .marketCap((long) marketCap)
                .updateTime(java.time.LocalDateTime.now().toString())
                .build();
    }
}
