package com.alphamind.model.dto;

import com.alphamind.news.provider.NewsArticle;
import com.alphamind.news.service.NewsResolution;
import com.alphamind.news.service.NewsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockNewsDTOTest {

    @Test
    void mapsResolutionFieldsAndArticlesVerbatim() {
        NewsSnapshot snapshot = new NewsSnapshot(
                "600519",
                List.of(
                        new NewsArticle("贵州茅台发布回购公告", LocalDateTime.of(2026, 9, 30, 19, 0), "http://example.com/1"),
                        new NewsArticle("白酒板块动态", null, null)),
                "eastmoney",
                Instant.now());
        NewsResolution resolution = new NewsResolution(
                snapshot, NewsResolution.Status.STALE_CACHE, 7200, "新闻源不可用");

        StockNewsDTO dto = StockNewsDTO.from("600519", resolution);

        assertEquals("600519", dto.getStockCode());
        assertEquals("eastmoney", dto.getSource());
        assertTrue(dto.getStale());
        assertEquals("新闻源不可用", dto.getWarning());
        assertEquals(2, dto.getArticles().size());
        assertEquals("贵州茅台发布回购公告", dto.getArticles().get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 0), dto.getArticles().get(0).getPublishedAt());
        assertEquals("http://example.com/1", dto.getArticles().get(0).getUrl());
        assertNull(dto.getArticles().get(1).getPublishedAt());
        assertNull(dto.getArticles().get(1).getUrl());
    }
}
