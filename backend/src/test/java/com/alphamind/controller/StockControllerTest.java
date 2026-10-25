package com.alphamind.controller;

import com.alphamind.news.provider.NewsArticle;
import com.alphamind.news.service.NewsResolution;
import com.alphamind.news.service.NewsSnapshot;
import com.alphamind.news.service.StockNewsService;
import com.alphamind.service.StockService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockControllerTest {
    private final StockNewsService newsService = mock(StockNewsService.class);
    private final StockController controller = new StockController(mock(StockService.class), newsService);

    @Test
    void newsEndpointReturnsStaleNewsWithWarning() {
        NewsSnapshot snapshot = new NewsSnapshot("600519",
                List.of(new NewsArticle("贵州茅台发布回购公告", null, "http://example.com/1")),
                "eastmoney", Instant.now());
        when(newsService.news("600519")).thenReturn(Optional.of(new NewsResolution(
                snapshot, NewsResolution.Status.STALE_CACHE, 7200, "新闻源不可用，当前展示最后成功缓存（7200 秒前）")));

        var response = controller.getStockNews("600519");

        assertEquals(200, response.getCode());
        assertTrue(response.getData().getStale());
        assertEquals("新闻源不可用，当前展示最后成功缓存（7200 秒前）", response.getData().getWarning());
        assertEquals("贵州茅台发布回购公告", response.getData().getArticles().get(0).getTitle());
    }

    @Test
    void newsEndpointReturns503InsteadOfPlaceholderWhenUnavailable() {
        when(newsService.news("600519")).thenReturn(Optional.empty());

        var response = controller.getStockNews("600519");

        assertEquals(503, response.getCode());
        assertNull(response.getData());
    }
}
