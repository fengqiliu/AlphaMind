package com.alphamind.service;

import com.alphamind.agent.MarketAgent;
import com.alphamind.agent.PortfolioAgent;
import com.alphamind.agent.SentimentAgent;
import com.alphamind.agent.TechnicalAgent;
import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.model.dto.TradeSignalDTO;
import com.alphamind.model.enums.AnalysisMode;
import com.alphamind.model.enums.SignalType;
import com.alphamind.model.enums.StrategyType;
import com.alphamind.news.service.NewsResolution;
import com.alphamind.news.service.StockNewsService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineOrchestratorTest {

    @Test
    void prefetchesNewsBeforeMarketStageAndHandsItToSentiment() {
        MarketAgent market = mock(MarketAgent.class);
        TechnicalAgent technical = mock(TechnicalAgent.class);
        SentimentAgent sentiment = mock(SentimentAgent.class);
        PortfolioAgent portfolio = mock(PortfolioAgent.class);
        StockNewsService news = mock(StockNewsService.class);
        CompletableFuture<Optional<NewsResolution>> prefetch = CompletableFuture.completedFuture(Optional.empty());
        when(news.prefetch("600519", SentimentAgent.NEWS_BODY_LIMIT, SentimentAgent.NEWS_BODY_MAX_CHARS)).thenReturn(prefetch);
        when(market.analyze(any())).thenAnswer(inv -> inv.getArgument(0));
        when(technical.analyze(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sentiment.analyze(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolio.analyze(any())).thenAnswer(inv -> {
            AnalysisReportDTO report = inv.getArgument(0);
            report.setTradeSignal(TradeSignalDTO.builder().type(SignalType.HOLD).build());
            return report;
        });

        new PipelineOrchestrator(market, technical, sentiment, portfolio, mock(DebateOrchestrator.class), news)
                .execute("600519", "贵州茅台", StrategyType.BALANCED, AnalysisMode.PIPELINE, event -> {});

        InOrder order = inOrder(news, market, sentiment);
        order.verify(news).prefetch("600519", SentimentAgent.NEWS_BODY_LIMIT, SentimentAgent.NEWS_BODY_MAX_CHARS);
        order.verify(market).analyze(any());
        order.verify(sentiment).setContext(SentimentAgent.NEWS_PREFETCH, prefetch);
        order.verify(sentiment).analyze(any());
    }
}
