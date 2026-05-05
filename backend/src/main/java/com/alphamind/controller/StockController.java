package com.alphamind.controller;

import com.alphamind.model.dto.ApiResponse;
import com.alphamind.model.dto.StockSearchResult;
import com.alphamind.model.dto.WeeklyStockRecommendation;
import com.alphamind.model.dto.WatchlistItem;
import com.alphamind.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 股票控制器 - 处理股票搜索和自选股管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * 搜索股票
     */
    @GetMapping("/search")
    public ApiResponse<List<StockSearchResult>> searchStocks(
            @RequestParam String query) {

        List<StockSearchResult> results = stockService.searchStocks(query);
        log.info("搜索股票: query={}, 结果数={}", query, results.size());

        return ApiResponse.success(results);
    }

    /**
     * 获取每周低位价值股推荐
     */
    @GetMapping("/recommendations/weekly")
    public ApiResponse<List<WeeklyStockRecommendation>> getWeeklyRecommendations() {
        List<WeeklyStockRecommendation> recommendations = stockService.getWeeklyValueRecommendations();
        log.info("获取每周低位价值股推荐: 数量={}", recommendations.size());
        return ApiResponse.success(recommendations);
    }

    /**
     * 获取股票详情
     */
    @GetMapping("/{code}")
    public ApiResponse<StockSearchResult> getStock(@PathVariable String code) {
        return stockService.getStock(code)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "股票不存在"));
    }

    /**
     * 获取自选股列表
     */
    @GetMapping("/watchlist")
    public ApiResponse<List<WatchlistItem>> getWatchlist(
            @RequestParam(defaultValue = "default") String userId) {

        List<WatchlistItem> watchlist = stockService.getWatchlist(userId);
        return ApiResponse.success(watchlist);
    }

    /**
     * 添加自选股
     */
    @PostMapping("/watchlist/{code}")
    public ApiResponse<Void> addToWatchlist(
            @PathVariable String code,
            @RequestParam(defaultValue = "default") String userId) {

        stockService.addToWatchlist(userId, code);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 移除自选股
     */
    @DeleteMapping("/watchlist/{code}")
    public ApiResponse<Void> removeFromWatchlist(
            @PathVariable String code,
            @RequestParam(defaultValue = "default") String userId) {

        stockService.removeFromWatchlist(userId, code);
        return ApiResponse.success("移除成功", null);
    }

    /**
     * 检查是否在自选股中
     */
    @GetMapping("/watchlist/{code}/check")
    public ApiResponse<Boolean> isInWatchlist(
            @PathVariable String code,
            @RequestParam(defaultValue = "default") String userId) {

        boolean isInWatchlist = stockService.isInWatchlist(userId, code);
        return ApiResponse.success(isInWatchlist);
    }
}
