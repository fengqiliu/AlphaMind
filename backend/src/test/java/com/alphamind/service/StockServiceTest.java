package com.alphamind.service;

import com.alphamind.market.provider.MarketQuote;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.QuoteResolution;
import com.alphamind.market.service.QuoteSnapshot;
import com.alphamind.market.service.StockQuoteService;
import com.alphamind.model.dto.WeeklyStockRecommendation;
import com.alphamind.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    @Test
    void shouldReturnThreeRankedWeeklyRecommendations() {
        StockService stockService = new StockService(
                mock(WatchlistItemRepository.class), new StockCatalog(), quotesAlwaysAvailable());

        List<WeeklyStockRecommendation> recommendations = stockService.getWeeklyValueRecommendations();

        assertEquals(3, recommendations.size(), "每周推荐应固定返回3只股票");
        assertEquals(1, recommendations.get(0).getRank());
        assertEquals(2, recommendations.get(1).getRank());
        assertEquals(3, recommendations.get(2).getRank());
        assertTrue(recommendations.stream().allMatch(item -> item.getCompositeScore() != null));
        assertTrue(recommendations.stream().allMatch(item -> item.getHighlights() != null && item.getHighlights().size() == 3));

        Set<String> uniqueIndustries = new HashSet<>();
        recommendations.forEach(item -> uniqueIndustries.add(item.getIndustry()));
        assertTrue(uniqueIndustries.size() >= 2, "推荐结果应尽量分散行业，避免三只全挤在同一赛道");
    }

    @Test
    void shouldReturnEmptyRecommendationsWhenQuotesUnavailable() {
        StockQuoteService stockQuoteService = mock(StockQuoteService.class);
        when(stockQuoteService.quotes(anyCollection())).thenReturn(Map.of());

        StockService stockService = new StockService(
                mock(WatchlistItemRepository.class), new StockCatalog(), stockQuoteService);

        assertTrue(stockService.getWeeklyValueRecommendations().isEmpty(),
                "行情源不可用时应返回空列表，而不是用虚构价格评分");
    }

    /** 给股票池内每只股票都造一条 LIVE 报价：当前价低于昨收，形成回撤场景。 */
    private static StockQuoteService quotesAlwaysAvailable() {
        StockQuoteService stockQuoteService = mock(StockQuoteService.class);
        when(stockQuoteService.quotes(anyCollection())).thenAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            Map<String, QuoteResolution> quotes = new LinkedHashMap<>();
            codes.forEach(code -> quotes.put(code, liveQuote(code)));
            return quotes;
        });
        return stockQuoteService;
    }

    private static QuoteResolution liveQuote(String code) {
        MarketQuote quote = new MarketQuote(
                code, "测试股票" + code,
                10.0, 10.5, 10.0, 10.6, 9.9,
                1_000_000L, 10_000_000.0,
                LocalDateTime.now());
        return new QuoteResolution(
                new QuoteSnapshot(quote, "测试行情源", Instant.now()),
                MarketDataResolution.Status.LIVE, 0);
    }
}
