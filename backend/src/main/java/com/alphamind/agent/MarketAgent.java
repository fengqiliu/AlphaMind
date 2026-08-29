package com.alphamind.agent;

import com.alphamind.market.provider.DailyKline;
import com.alphamind.market.provider.MarketQuote;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.MarketDataSnapshot;
import com.alphamind.market.service.ResilientMarketDataService;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.ChatMessage;
import com.alphamind.model.dto.MarketDataDTO;
import com.alphamind.model.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        MarketDataSnapshot snapshot = resolution.snapshot();
        MarketQuote quote = snapshot.quote();
        List<DailyKline> bars = snapshot.dailyKlines();
        List<double[]> klines = bars.stream()
                .map(bar -> new double[]{bar.open(), bar.close(), bar.low(), bar.high()})
                .toList();
        List<Long> volumes = bars.stream().map(DailyKline::volume).toList();
        double change = round(quote.current() - quote.previousClose(), 2);
        double changePercent = quote.previousClose() > 0
                ? round(change * 100 / quote.previousClose(), 2) : 0D;
        double turnoverRate = bars.isEmpty() ? 0D : bars.get(bars.size() - 1).turnoverRate();

        return MarketDataDTO.builder()
                .stockCode(snapshot.stockCode())
                .stockName(snapshot.stockName())
                .currentPrice(round(quote.current(), 2))
                .change(change)
                .changePercent(changePercent)
                .open(round(quote.open(), 2))
                .high(round(quote.high(), 2))
                .low(round(quote.low(), 2))
                .volume(quote.volume())
                .amount(quote.amount())
                .turnoverRate(round(turnoverRate, 2))
                // 新浪报价与东方财富K线接口不返回这些字段，保持 null，禁止把 0 当成估值结论。
                .pe(null)
                .pb(null)
                .marketCap(null)
                .updateTime(quote.quotedAt().toString())
                .dataSource(snapshot.quoteSource().toUpperCase() + "+" + snapshot.klineSource().toUpperCase())
                .dataStatus(resolution.status().name())
                .stale(resolution.status() == MarketDataResolution.Status.STALE_CACHE)
                .cacheAgeSeconds(resolution.cacheAgeSeconds())
                .dataWarning(resolution.warning())
                .klineDates(bars.stream().map(bar -> bar.tradeDate().toString()).toList())
                .klines(klines)
                .klineVolumes(volumes)
                .ma5(calculateMA(klines, 5))
                .ma10(calculateMA(klines, 10))
                .ma20(calculateMA(klines, 20))
                .ma60(calculateMA(klines, 60))
                .build();
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

    private List<Double> calculateMA(List<double[]> klines, int period) {
        List<Double> averages = new ArrayList<>();
        for (int index = 0; index < klines.size(); index++) {
            if (index < period - 1) {
                averages.add(null);
                continue;
            }
            double total = 0;
            for (int cursor = index - period + 1; cursor <= index; cursor++) {
                total += klines.get(cursor)[1];
            }
            averages.add(round(total / period, 2));
        }
        return averages;
    }

    private static double round(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }
}
