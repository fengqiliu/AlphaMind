package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 行情Agent - 负责采集和处理股票行情数据
 */
@Slf4j
@Component
public class MarketAgent extends BaseAgent {

    public MarketAgent() {
        super(AgentType.MARKET);
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始采集行情数据: " + report.getStockCode());

        try {
            // TODO: 接入真实数据源 (Tushare/AKShare)
            MarketDataDTO marketData = fetchMarketData(report.getStockCode());

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

        String response = String.format("""
            根据当前行情数据分析：

            **%s (%s)**
            - 当前价格: ¥%.2f
            - 涨跌幅: %+.2f%% (¥%+.2f)
            - 开盘: ¥%.2f | 最高: ¥%.2f | 最低: ¥%.2f
            - 成交量: %.2f万手
            - 换手率: %.2f%%
            - 市盈率(PE): %.2f | 市净率(PB): %.2f
            - 总市值: %.2f亿

            数据更新时间: %s
            """,
            marketData.getStockName(),
            marketData.getStockCode(),
            marketData.getCurrentPrice(),
            marketData.getChangePercent(),
            marketData.getChange(),
            marketData.getOpen(),
            marketData.getHigh(),
            marketData.getLow(),
            marketData.getVolume() / 10000.0,
            marketData.getTurnoverRate(),
            marketData.getPe(),
            marketData.getPb(),
            marketData.getMarketCap() / 1e8,
            marketData.getUpdateTime()
        );

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(getModelName())
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
            - 客观准确，不添加主观判断
            - 数据异常时需要特别标注
            - 涉及专业术语时简要解释
            """;
    }

    /**
     * 获取市场数据 (模拟实现)
     * TODO: 接入 Tushare Pro 或 AKShare
     */
    private MarketDataDTO fetchMarketData(String stockCode) {
        // 模拟数据
        return MarketDataDTO.builder()
                .stockCode(stockCode)
                .stockName("贵州茅台")
                .currentPrice(1680.50)
                .change(25.30)
                .changePercent(1.53)
                .open(1655.20)
                .high(1692.00)
                .low(1648.50)
                .volume(3250000L)
                .amount(5400000000.0)
                .turnoverRate(0.26)
                .pe(35.8)
                .pb(12.5)
                .marketCap(2110000000000L)
                .updateTime(java.time.LocalDateTime.now().toString())
                .build();
    }
}
