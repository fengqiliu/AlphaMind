package com.alphamind.service;

import com.alphamind.model.dto.StockSearchResult;
import com.alphamind.model.dto.WatchlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 股票服务 - 处理股票搜索和自选股管理
 */
@Slf4j
@Service
public class StockService {

    // 模拟股票数据库
    private static final Map<String, StockSearchResult> STOCK_DB = new ConcurrentHashMap<>();

    static {
        // 初始化一些股票数据
        STOCK_DB.put("600519", StockSearchResult.builder()
                .code("600519").name("贵州茅台").industry("白酒").market("上海主板")
                .currentPrice(1680.50).changePercent(1.53).build());

        STOCK_DB.put("000858", StockSearchResult.builder()
                .code("000858").name("五粮液").industry("白酒").market("深圳主板")
                .currentPrice(165.30).changePercent(0.82).build());

        STOCK_DB.put("000001", StockSearchResult.builder()
                .code("000001").name("平安银行").industry("银行").market("深圳主板")
                .currentPrice(12.35).changePercent(-0.45).build());

        STOCK_DB.put("600036", StockSearchResult.builder()
                .code("600036").name("招商银行").industry("银行").market("上海主板")
                .currentPrice(35.60).changePercent(1.25).build());

        STOCK_DB.put("601318", StockSearchResult.builder()
                .code("601318").name("中国平安").industry("保险").market("上海主板")
                .currentPrice(48.25).changePercent(0.68).build());

        STOCK_DB.put("000333", StockSearchResult.builder()
                .code("000333").name("美的集团").industry("家电").market("深圳主板")
                .currentPrice(62.80).changePercent(-0.35).build());

        STOCK_DB.put("002594", StockSearchResult.builder()
                .code("002594").name("比亚迪").industry("汽车").market("深圳主板")
                .currentPrice(238.50).changePercent(2.15).build());

        STOCK_DB.put("600276", StockSearchResult.builder()
                .code("600276").name("恒瑞医药").industry("医药").market("上海主板")
                .currentPrice(52.30).changePercent(-1.20).build());

        STOCK_DB.put("300750", StockSearchResult.builder()
                .code("300750").name("宁德时代").industry("新能源").market("创业板")
                .currentPrice(185.60).changePercent(3.25).build());

        STOCK_DB.put("688981", StockSearchResult.builder()
                .code("688981").name("中芯国际").industry("半导体").market("科创板")
                .currentPrice(52.80).changePercent(-2.35).build());
    }

    // 用户自选股存储
    private final Map<String, Set<String>> userWatchlist = new ConcurrentHashMap<>();

    /**
     * 搜索股票
     */
    public List<StockSearchResult> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String lowerQuery = query.toLowerCase();
        List<StockSearchResult> results = new ArrayList<>();

        STOCK_DB.forEach((code, stock) -> {
            if (code.contains(lowerQuery) ||
                    stock.getName().toLowerCase().contains(lowerQuery) ||
                    stock.getIndustry().toLowerCase().contains(lowerQuery)) {
                results.add(stock);
            }
        });

        return results;
    }

    /**
     * 获取股票详情
     */
    public Optional<StockSearchResult> getStock(String code) {
        return Optional.ofNullable(STOCK_DB.get(code));
    }

    /**
     * 添加自选股
     */
    public void addToWatchlist(String userId, String stockCode) {
        userWatchlist.computeIfAbsent(userId, k -> new HashSet<>()).add(stockCode);
        log.info("用户 {} 添加自选股: {}", userId, stockCode);
    }

    /**
     * 移除自选股
     */
    public void removeFromWatchlist(String userId, String stockCode) {
        if (userWatchlist.containsKey(userId)) {
            userWatchlist.get(userId).remove(stockCode);
            log.info("用户 {} 移除自选股: {}", userId, stockCode);
        }
    }

    /**
     * 获取用户自选股列表
     */
    public List<WatchlistItem> getWatchlist(String userId) {
        Set<String> codes = userWatchlist.getOrDefault(userId, new HashSet<>());
        List<WatchlistItem> items = new ArrayList<>();

        for (String code : codes) {
            StockSearchResult stock = STOCK_DB.get(code);
            if (stock != null) {
                items.add(WatchlistItem.builder()
                        .stockCode(stock.getCode())
                        .stockName(stock.getName())
                        .addedAt(LocalDateTime.now().minusDays(new Random().nextInt(30)))
                        .currentPrice(stock.getCurrentPrice())
                        .change(stock.getCurrentPrice() * stock.getChangePercent() / 100)
                        .changePercent(stock.getChangePercent())
                        .build());
            }
        }

        return items;
    }

    /**
     * 检查是否在自选股中
     */
    public boolean isInWatchlist(String userId, String stockCode) {
        return userWatchlist.getOrDefault(userId, new HashSet<>()).contains(stockCode);
    }
}
