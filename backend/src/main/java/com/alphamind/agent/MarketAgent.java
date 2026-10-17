package com.alphamind.agent;

import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.ResilientMarketDataService;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.dto.MarketDataDTO;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 行情 Agent - 使用 Provider 链采集真实实时报价和日K线。 */
@Slf4j
@Component
public class MarketAgent extends BaseAgent {

    private final ResilientMarketDataService resilientMarketDataService;

    public MarketAgent(ResilientMarketDataService resilientMarketDataService) {
        super(AgentType.MARKET);
        this.resilientMarketDataService = resilientMarketDataService;
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始采集行情数据: " + report.getStockCode());
        try {
            MarketDataDTO marketData = fetchMarketData(report.getStockCode(), report.getStockName());
            marketData.setAiSummary(generateMarketSummary(marketData));
            report.setMarketData(marketData);
            setContext("marketData", marketData);
            logInfo("行情数据采集完成，当前价格: " + marketData.getCurrentPrice());
            return report;
        } catch (Exception exception) {
            logError("行情数据采集失败", exception);
            throw new RuntimeException("行情数据采集失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        if (marketData == null) {
            return buildErrorMessage("暂无行情数据，请先发起分析。");
        }
        String response = llmCall(getSystemPrompt(), buildMarketPrompt(marketData, userMessage.getContent()));
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response != null ? response : buildMarketTemplateResponse(marketData))
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
                你是一名专业的股票行情分析师，负责采集和解读股票市场的实时行情数据。
                你的职责：解读实时价格、涨跌幅、成交量和日K线；结合开盘、最高、最低价判断当日走势。
                回答必须客观准确。没有估值或市值数据时，明确说明数据源未提供，不得编造。
                """;
    }

    private MarketDataDTO fetchMarketData(String stockCode, String stockNameHint) {
        MarketDataResolution resolution = resilientMarketDataService.fetch(stockCode, stockNameHint);
        return com.alphamind.market.service.MarketDataMapper.toDTO(resolution);
    }

    private String generateMarketSummary(MarketDataDTO data) {
        String prompt = String.format(
                "请用50字以内简要分析 %s(%s) 的行情：当前价¥%.2f，涨跌%+.2f%%，换手率%.2f%%。估值数据源未提供。",
                data.getStockName(), data.getStockCode(), data.getCurrentPrice(),
                data.getChangePercent(), data.getTurnoverRate());
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : buildDefaultSummary(data);
    }

    private String buildDefaultSummary(MarketDataDTO data) {
        return String.format("%s 当前报价¥%.2f，%s%.2f%%。换手率%.2f%%；估值数据源未提供。",
                data.getStockName(), data.getCurrentPrice(),
                data.getChangePercent() >= 0 ? "上涨" : "下跌",
                Math.abs(data.getChangePercent()), data.getTurnoverRate());
    }

    private String buildMarketPrompt(MarketDataDTO data, String userQuestion) {
        return String.format("""
                股票行情数据：
                - 股票：%s (%s)
                - 当前价：¥%.2f，涨跌：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万股，换手率：%.2f%%
                - PE、PB、总市值：当前行情接口未提供

                用户问题：%s
                """,
                data.getStockName(), data.getStockCode(), data.getCurrentPrice(),
                data.getChangePercent(), data.getChange(), data.getOpen(), data.getHigh(), data.getLow(),
                data.getVolume() / 10000.0, data.getTurnoverRate(), userQuestion);
    }

    private String buildMarketTemplateResponse(MarketDataDTO data) {
        return String.format("""
                **%s (%s) 行情数据**

                - 当前价格：¥%.2f
                - 涨跌幅：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万股 | 换手率：%.2f%%
                - PE、市净率和总市值：当前行情接口未提供
                - 数据更新时间：%s
                """,
                data.getStockName(), data.getStockCode(), data.getCurrentPrice(), data.getChangePercent(),
                data.getChange(), data.getOpen(), data.getHigh(), data.getLow(), data.getVolume() / 10000.0,
                data.getTurnoverRate(), data.getUpdateTime());
    }

    private ChatMessage buildErrorMessage(String content) {
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .agentType(agentType)
                .agentName(agentType.getName())
                .timestamp(LocalDateTime.now())
                .build();
    }

}
