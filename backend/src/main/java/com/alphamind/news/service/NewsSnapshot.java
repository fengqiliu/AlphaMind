package com.alphamind.news.service;

import com.alphamind.news.provider.NewsArticle;

import java.time.Instant;
import java.util.List;

/**
 * 一次成功抓取的个股新闻集合。{@code retrievedAt} 是本系统成功拿到它的时间——
 * 缓存新鲜度按它计算，新闻源请求失败时据此返回带过期标记的最后成功数据。
 */
public record NewsSnapshot(String stockCode, List<NewsArticle> articles, String sourceName, Instant retrievedAt) {

    public NewsSnapshot {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
