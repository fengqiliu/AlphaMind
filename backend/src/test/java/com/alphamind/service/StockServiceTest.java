package com.alphamind.service;

import com.alphamind.model.dto.WeeklyStockRecommendation;
import com.alphamind.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StockServiceTest {

    @Test
    void shouldReturnThreeRankedWeeklyRecommendations() {
        StockService stockService = new StockService(mock(WatchlistItemRepository.class));

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
}