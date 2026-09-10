package com.alphamind.market.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Eastmoney public endpoint adapter for daily A-share K-line history. */
@Component
public class EastmoneyKlineMarketDataProvider implements MarketDataProvider {

    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public EastmoneyKlineMarketDataProvider(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${alphamind.market.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${alphamind.market.read-timeout-ms:5000}") long readTimeoutMs) {
        this(restTemplateBuilder
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .readTimeout(Duration.ofMillis(readTimeoutMs))
                        .build(),
                objectMapper);
    }

    EastmoneyKlineMarketDataProvider(RestOperations restOperations, ObjectMapper objectMapper) {
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceName() {
        return "eastmoney";
    }

    @Override
    public Optional<List<DailyKline>> fetchDailyKlines(String stockCode, int limit) {
        if (!isAshareCode(stockCode) || limit <= 0) {
            return Optional.empty();
        }
        try {
            String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                    + "?secid=" + toEastmoneySecId(stockCode)
                    + "&klt=101&fqt=1&lmt=" + limit + "&end=20500101"
                    + "&fields1=f1,f2,f3,f4,f5,f6"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";
            String response = restOperations.getForObject(url, String.class);
            return parseKlines(response, limit, objectMapper);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /** Package-visible parser for deterministic tests against saved API payloads. */
    static Optional<List<DailyKline>> parseKlines(String response, int limit, ObjectMapper objectMapper) {
        try {
            if (response == null || response.isBlank()) {
                return Optional.empty();
            }
            JsonNode bars = objectMapper.readTree(response).path("data").path("klines");
            if (!bars.isArray()) {
                return Optional.empty();
            }
            List<DailyKline> result = new ArrayList<>();
            for (JsonNode barNode : bars) {
                String[] fields = barNode.asText().split(",", -1);
                if (fields.length < 11) {
                    continue;
                }
                try {
                    result.add(new DailyKline(
                            LocalDate.parse(fields[0]),
                            parseDouble(fields[1]),
                            parseDouble(fields[2]),
                            parseDouble(fields[4]),
                            parseDouble(fields[3]),
                            Math.round(parseDouble(fields[5]) * 100), // 东方财富日K成交量单位为手
                            parseDouble(fields[6]),
                            parseDouble(fields[10])
                    ));
                } catch (RuntimeException ignored) {
                    // One malformed bar must not make an otherwise usable response unusable.
                }
            }
            result.sort(Comparator.comparing(DailyKline::tradeDate));
            if (result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.size() > limit
                    ? List.copyOf(result.subList(result.size() - limit, result.size()))
                    : List.copyOf(result));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private static boolean isAshareCode(String stockCode) {
        return stockCode != null && stockCode.matches("\\d{6}");
    }

    private static String toEastmoneySecId(String stockCode) {
        return (stockCode.startsWith("6") || stockCode.startsWith("9") ? "1." : "0.") + stockCode;
    }
}
