package com.alphamind.model.dto;

import com.alphamind.news.service.NewsResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个股最新资讯。标题、时间、链接均来自真实新闻源；
 * 新闻源与缓存都不可用时接口返回 503 提示，绝不用占位新闻冒充。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNewsDTO {

    private String stockCode;

    /** 新闻来源（如 eastmoney）。 */
    private String source;

    /** true 表示新闻来自过期缓存，而非实时新闻源。 */
    private Boolean stale;

    /** 过期缓存时的中文降级提示（含缓存年龄），实时/新鲜缓存为 null。 */
    private String warning;

    private List<NewsItem> articles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsItem {
        private String title;
        private LocalDateTime publishedAt;
        private String url;
    }

    public static StockNewsDTO from(String stockCode, NewsResolution resolution) {
        return StockNewsDTO.builder()
                .stockCode(stockCode)
                .source(resolution.snapshot().sourceName())
                .stale(resolution.stale())
                .warning(resolution.warning())
                .articles(resolution.snapshot().articles().stream()
                        .map(article -> NewsItem.builder()
                                .title(article.title())
                                .publishedAt(article.publishedAt())
                                .url(article.url())
                                .build())
                        .toList())
                .build();
    }
}
