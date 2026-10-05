package com.alphamind.news.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EastmoneyNewsProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesEastmoneyNewsPayloadWithTitlesAndTimes() {
        String response = """
                {"code":1,"message":"success","data":{"page_index":1,"totle_hits":5000,"list":[
                  {"Art_ShowTime":"2026-09-30 19:14:40","Art_Code":"202609303887828627","Np_dst":"CMS",
                   "Art_Title":"“双节”白酒冷热考：动销向名酒、刚需集中 供需拐点有待Q4",
                   "Art_Url":"http://finance.eastmoney.com/a/202609303887828627.html"},
                  {"Art_ShowTime":"2026-09-30 17:27:36","Art_Code":"202609303887782560","Np_dst":"CMS",
                   "Art_Title":"N力勤获资金净流入14.76亿元",
                   "Art_Url":"http://finance.eastmoney.com/a/202609303887782560.html"}
                ]}}
                """;

        Optional<List<NewsArticle>> result = EastmoneyNewsProvider.parseNews(response, 20, mapper);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        NewsArticle first = result.get().get(0);
        assertEquals("“双节”白酒冷热考：动销向名酒、刚需集中 供需拐点有待Q4", first.title());
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 14, 40), first.publishedAt());
        assertEquals("http://finance.eastmoney.com/a/202609303887828627.html", first.url());
    }

    @Test
    void limitsArticleCountToRequestedSize() {
        String response = """
                {"data":{"list":[
                  {"Art_Title":"新闻一","Art_ShowTime":"2026-09-30 17:27:36"},
                  {"Art_Title":"新闻二","Art_ShowTime":"2026-09-30 16:27:36"},
                  {"Art_Title":"新闻三","Art_ShowTime":"2026-09-30 15:27:36"}
                ]}}
                """;

        Optional<List<NewsArticle>> result = EastmoneyNewsProvider.parseNews(response, 2, mapper);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals("新闻一", result.get().get(0).title());
    }

    @Test
    void skipsEntriesWithoutTitleAndRejectsEmptyPayload() {
        String withBadEntry = """
                {"data":{"list":[
                  {"Art_ShowTime":"2026-09-30 17:27:36"},
                  {"Art_Title":"正常新闻","Art_ShowTime":"2026-09-30 16:27:36"}
                ]}}
                """;
        Optional<List<NewsArticle>> result = EastmoneyNewsProvider.parseNews(withBadEntry, 20, mapper);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("正常新闻", result.get().get(0).title());

        assertTrue(EastmoneyNewsProvider.parseNews(null, 20, mapper).isEmpty());
        assertTrue(EastmoneyNewsProvider.parseNews("", 20, mapper).isEmpty());
        assertTrue(EastmoneyNewsProvider.parseNews("{\"data\":{}}", 20, mapper).isEmpty());
        assertTrue(EastmoneyNewsProvider.parseNews("{\"data\":{\"list\":[]}}", 20, mapper).isEmpty());
    }

    @Test
    void fetchNewsRejectsInvalidStockCodeWithoutNetworkCall() {
        EastmoneyNewsProvider provider = new EastmoneyNewsProvider(null, mapper);

        assertTrue(provider.fetchNews(null, 20).isEmpty());
        assertTrue(provider.fetchNews("60051", 20).isEmpty());
        assertTrue(provider.fetchNews("600519", 0).isEmpty());
        assertEquals("eastmoney", provider.sourceName());
    }
}
