package com.alphamind.news.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 东方财富个股新闻列表适配器，供舆情分析使用真实新闻标题。 */
@Component
public class EastmoneyNewsProvider {

    public static final String SOURCE_NAME = "eastmoney";

    private static final DateTimeFormatter SHOW_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public EastmoneyNewsProvider(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${alphamind.news.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${alphamind.news.read-timeout-ms:5000}") long readTimeoutMs) {
        this(restTemplateBuilder
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .readTimeout(Duration.ofMillis(readTimeoutMs))
                        .build(),
                objectMapper);
    }

    EastmoneyNewsProvider(RestOperations restOperations, ObjectMapper objectMapper) {
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
    }

    public String sourceName() {
        return SOURCE_NAME;
    }

    /** 拉取个股新闻，任何失败都返回 empty——上游不可用由调用方显式降级，这里绝不编造新闻。 */
    public Optional<List<NewsArticle>> fetchNews(String stockCode, int limit) {
        if (!isAshareCode(stockCode) || limit <= 0) {
            return Optional.empty();
        }
        try {
            String url = "https://np-listapi.eastmoney.com/comm/web/getListInfo"
                    + "?client=web&mTypeAndCode=" + toEastmoneySecId(stockCode)
                    + "&type=1&pageSize=" + limit + "&pageIndex=1";
            String response = restOperations.getForObject(url, String.class);
            return parseNews(response, limit, objectMapper);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /** Package-visible parser for deterministic tests against saved API payloads. */
    static Optional<List<NewsArticle>> parseNews(String response, int limit, ObjectMapper objectMapper) {
        try {
            if (response == null || response.isBlank() || limit <= 0) {
                return Optional.empty();
            }
            JsonNode list = objectMapper.readTree(response).path("data").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return Optional.empty();
            }
            List<NewsArticle> articles = new ArrayList<>();
            for (JsonNode node : list) {
                String title = node.path("Art_Title").asText(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                articles.add(new NewsArticle(
                        title,
                        parseTime(node.path("Art_ShowTime").asText(null)),
                        node.path("Art_Url").asText(null)));
                if (articles.size() >= limit) {
                    break;
                }
            }
            if (articles.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(articles));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim(), SHOW_TIME_FORMAT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isAshareCode(String stockCode) {
        return stockCode != null && stockCode.matches("\\d{6}");
    }

    private static String toEastmoneySecId(String stockCode) {
        return (stockCode.startsWith("6") || stockCode.startsWith("9") ? "1." : "0.") + stockCode;
    }
}
