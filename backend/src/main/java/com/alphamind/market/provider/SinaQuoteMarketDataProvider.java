package com.alphamind.market.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Sina public endpoint adapter for a real-time A-share quote. */
@Component
public class SinaQuoteMarketDataProvider implements MarketDataProvider {

    private static final Charset GBK = Charset.forName("GBK");
    private static final DateTimeFormatter SINA_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestOperations restOperations;

    public SinaQuoteMarketDataProvider(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${alphamind.market.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${alphamind.market.read-timeout-ms:5000}") long readTimeoutMs) {
        this(restTemplateBuilder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build());
    }

    SinaQuoteMarketDataProvider(RestOperations restOperations) {
        this.restOperations = restOperations;
    }

    @Override
    public String sourceName() {
        return "sina";
    }

    @Override
    public Optional<MarketQuote> fetchQuote(String stockCode, String stockNameHint) {
        if (!isAshareCode(stockCode)) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://finance.sina.com.cn");
            headers.set("User-Agent", "Mozilla/5.0 (compatible; AlphaMind/1.0)");
            String url = "https://hq.sinajs.cn/list=" + toSinaSymbol(stockCode);
            ResponseEntity<byte[]> response = restOperations.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            if (response.getBody() == null) {
                return Optional.empty();
            }
            return parseQuote(stockCode, new String(response.getBody(), GBK));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /**
     * Package-visible parsing entry point so provider tests can use captured
     * upstream responses without opening a network connection.
     */
    static Optional<MarketQuote> parseQuote(String stockCode, String body) {
        try {
            int start = body.indexOf('"') + 1;
            int end = body.lastIndexOf('"');
            if (start < 1 || end <= start) {
                return Optional.empty();
            }
            String[] fields = body.substring(start, end).split(",", -1);
            if (fields.length < 10) {
                return Optional.empty();
            }
            double current = parseDouble(fields[3]);
            if (current <= 0) {
                return Optional.empty();
            }
            LocalDateTime quotedAt = parseTimestamp(fields);
            return Optional.of(new MarketQuote(
                    stockCode,
                    fields[0].trim(),
                    parseDouble(fields[1]),
                    parseDouble(fields[2]),
                    current,
                    parseDouble(fields[4]),
                    parseDouble(fields[5]),
                    Math.round(parseDouble(fields[8])), // 新浪该字段单位为股
                    parseDouble(fields[9]),
                    quotedAt
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static LocalDateTime parseTimestamp(String[] fields) {
        if (fields.length > 31 && !fields[30].isBlank() && !fields[31].isBlank()) {
            try {
                return LocalDateTime.parse(fields[30] + " " + fields[31], SINA_TIMESTAMP);
            } catch (RuntimeException ignored) {
                // Use retrieval time when the source omits or changes its timestamp fields.
            }
        }
        return LocalDateTime.now();
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private static boolean isAshareCode(String stockCode) {
        return stockCode != null && stockCode.matches("\\d{6}");
    }

    private static String toSinaSymbol(String stockCode) {
        if (stockCode.startsWith("6") || stockCode.startsWith("9")) {
            return "sh" + stockCode;
        }
        if (stockCode.startsWith("8") || stockCode.startsWith("4")) {
            return "bj" + stockCode;
        }
        return "sz" + stockCode;
    }
}
