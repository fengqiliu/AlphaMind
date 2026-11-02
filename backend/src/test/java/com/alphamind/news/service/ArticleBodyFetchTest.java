package com.alphamind.news.service;

import com.alphamind.news.provider.EastmoneyNewsProvider;
import com.alphamind.news.provider.NewsArticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleBodyFetchTest {
    private static final String U1 = "http://finance.eastmoney.com/a/1.html";
    private static final String U2 = "http://finance.eastmoney.com/a/2.html";
    private static final String U3 = "http://finance.eastmoney.com/a/3.html";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final EastmoneyNewsProvider provider = mock(EastmoneyNewsProvider.class);
    private final StockNewsService service = new StockNewsService(provider, mock(NewsCacheService.class), executor);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static NewsArticle article(String title, String url) {
        return new NewsArticle(title, null, url);
    }

    /** 第一篇只有在第二篇已开始抓取后才返回：串行抓取时会超时拿不到正文。 */
    @Test
    void fetchesBodiesConcurrently() {
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(provider.fetchArticleBody(U1, 500)).thenAnswer(inv ->
                secondStarted.await(2, TimeUnit.SECONDS) ? Optional.of("正文一") : Optional.empty());
        when(provider.fetchArticleBody(U2, 500)).thenAnswer(inv -> {
            secondStarted.countDown();
            return Optional.of("正文二");
        });

        Map<String, String> bodies = service.articleBodiesByTitle(
                List.of(article("标题一", U1), article("标题二", U2)), 2, 500);

        assertEquals(List.of("标题一", "标题二"), List.copyOf(bodies.keySet()));
    }

    @Test
    void keepsArticleOrderAndFillsFromLaterCandidatesWhenOneFails() {
        when(provider.fetchArticleBody(U1, 500)).thenReturn(Optional.empty());
        when(provider.fetchArticleBody(U2, 500)).thenReturn(Optional.of("正文二"));
        when(provider.fetchArticleBody(U3, 500)).thenReturn(Optional.of("正文三"));

        Map<String, String> bodies = service.articleBodiesByTitle(
                List.of(article("标题一", U1), article("标题二", U2), article("标题三", U3)), 2, 500);

        assertEquals(List.of("标题二", "标题三"), List.copyOf(bodies.keySet()));
    }

    @Test
    void reusesCachedBodiesAcrossCalls() {
        when(provider.fetchArticleBody(U1, 500)).thenReturn(Optional.of("正文一"));
        List<NewsArticle> articles = List.of(article("标题一", U1));

        service.articleBodiesByTitle(articles, 1, 500);
        Map<String, String> second = service.articleBodiesByTitle(articles, 1, 500);

        assertEquals("正文一", second.get("标题一"));
        verify(provider, times(1)).fetchArticleBody(U1, 500);
    }

    @Test
    void failuresAreNotCached() {
        when(provider.fetchArticleBody(U1, 500)).thenReturn(Optional.empty()).thenReturn(Optional.of("正文一"));
        List<NewsArticle> articles = List.of(article("标题一", U1));

        assertTrue(service.articleBodiesByTitle(articles, 1, 500).isEmpty());
        assertEquals("正文一", service.articleBodiesByTitle(articles, 1, 500).get("标题一"));
    }

    @Test
    void prefetchReturnsNewsAndWarmsBodyCache() {
        NewsCacheService cache = mock(NewsCacheService.class);
        StockNewsService prefetching = new StockNewsService(provider, cache, executor);
        when(cache.getFresh("600519")).thenReturn(Optional.empty());
        when(provider.fetchNews("600519", 20)).thenReturn(Optional.of(List.of(article("标题一", U1))));
        when(provider.sourceName()).thenReturn("eastmoney");
        when(provider.fetchArticleBody(U1, 500)).thenReturn(Optional.of("正文一"));

        var resolution = prefetching.prefetch("600519", 3, 500).join().orElseThrow();
        var bodies = prefetching.articleBodiesByTitle(resolution.snapshot().articles(), 3, 500);

        assertEquals("标题一", resolution.snapshot().articles().get(0).title());
        assertEquals("正文一", bodies.get("标题一"));
        verify(provider, times(1)).fetchArticleBody(U1, 500);
    }
}
