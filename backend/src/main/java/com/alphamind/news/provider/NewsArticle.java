package com.alphamind.news.provider;

import java.time.LocalDateTime;

/** 一条个股新闻。标题、发布时间、链接均来自真实新闻源，缺哪个字段就为 null，不用占位值冒充。 */
public record NewsArticle(String title, LocalDateTime publishedAt, String url) {
}
