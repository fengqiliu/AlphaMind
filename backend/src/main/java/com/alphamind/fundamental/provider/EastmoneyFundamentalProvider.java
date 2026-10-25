package com.alphamind.fundamental.provider;

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
import java.util.List;
import java.util.Optional;

/**
 * 东方财富数据中心适配器：估值历史（RPT_VALUEANALYSIS_DET）与主要财务指标（RPT_F10_FINANCE_MAINFINADATA）。
 * 任何失败都返回 empty——上游不可用由调用方显式降级，这里绝不补默认值。ETF 等无财报证券返回 empty。
 */
@Component
public class EastmoneyFundamentalProvider {

    public static final String SOURCE_NAME = "eastmoney-datacenter";
    /** 少于约一年的有效样本不计算历史分位。 */
    public static final int MIN_PERCENTILE_SAMPLES = 240;

    private static final String BASE = "https://datacenter.eastmoney.com/securities/api/data/get";
    private static final String VALUATION_COLUMNS =
            "TRADE_DATE,CLOSE_PRICE,TOTAL_MARKET_CAP,PE_TTM,PB_MRQ,PCF_OCF_TTM,PS_TTM,PEG_CAR";

    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final int historyDays;

    @Autowired
    public EastmoneyFundamentalProvider(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${alphamind.fundamental.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${alphamind.fundamental.read-timeout-ms:8000}") long readTimeoutMs,
            @Value("${alphamind.fundamental.valuation-history-days:1250}") int historyDays) {
        this(restTemplateBuilder
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .readTimeout(Duration.ofMillis(readTimeoutMs))
                        .build(),
                objectMapper, historyDays);
    }

    EastmoneyFundamentalProvider(RestOperations restOperations, ObjectMapper objectMapper, int historyDays) {
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
        this.historyDays = historyDays;
    }

    public String sourceName() {
        return SOURCE_NAME;
    }

    /** 历史窗口（交易日数），用于提示分位口径。 */
    public int historyDays() {
        return historyDays;
    }

    public Optional<ValuationMetrics> fetchValuation(String stockCode) {
        if (!isAshareCode(stockCode)) return Optional.empty();
        try {
            String url = BASE + "?type=RPT_VALUEANALYSIS_DET&sty=" + VALUATION_COLUMNS
                    + "&filter=(SECURITY_CODE%3D%22" + stockCode + "%22)"
                    + "&p=1&ps=" + historyDays + "&sr=-1&st=TRADE_DATE";
            return parseValuation(restOperations.getForObject(url, String.class), objectMapper);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<FinancialMetrics> fetchFinancials(String stockCode) {
        if (!isAshareCode(stockCode)) return Optional.empty();
        try {
            String url = BASE + "?type=RPT_F10_FINANCE_MAINFINADATA&sty=APP_F10_MAINFINADATA"
                    + "&filter=(SECUCODE%3D%22" + toSecuCode(stockCode) + "%22)"
                    + "&p=1&ps=1&sr=-1&st=REPORT_DATE";
            return parseFinancials(restOperations.getForObject(url, String.class), objectMapper);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /** Package-visible parser for deterministic tests. 行按交易日降序，第一行为最新交易日。 */
    static Optional<ValuationMetrics> parseValuation(String response, ObjectMapper objectMapper) {
        try {
            JsonNode rows = rows(response, objectMapper);
            if (rows == null) return Optional.empty();
            JsonNode latest = rows.get(0);
            LocalDate tradeDate = date(latest, "TRADE_DATE");
            if (tradeDate == null) return Optional.empty();
            List<Double> pe = new ArrayList<>(), pb = new ArrayList<>();
            LocalDate from = tradeDate;
            for (JsonNode row : rows) {
                Double peValue = number(row, "PE_TTM"), pbValue = number(row, "PB_MRQ");
                if (peValue != null && peValue > 0) pe.add(peValue);
                if (pbValue != null && pbValue > 0) pb.add(pbValue);
                LocalDate day = date(row, "TRADE_DATE");
                if (day != null && day.isBefore(from)) from = day;
            }
            Double peTtm = number(latest, "PE_TTM"), pbMrq = number(latest, "PB_MRQ");
            return Optional.of(new ValuationMetrics(tradeDate,
                    number(latest, "CLOSE_PRICE"), number(latest, "TOTAL_MARKET_CAP"),
                    peTtm, pbMrq, number(latest, "PCF_OCF_TTM"), number(latest, "PS_TTM"), number(latest, "PEG_CAR"),
                    percentile(peTtm, pe), percentile(pbMrq, pb), pe.size(), pb.size(), from));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /** Package-visible parser for deterministic tests. 第一行为最新报告期。 */
    static Optional<FinancialMetrics> parseFinancials(String response, ObjectMapper objectMapper) {
        try {
            JsonNode rows = rows(response, objectMapper);
            if (rows == null) return Optional.empty();
            JsonNode r = rows.get(0);
            LocalDate reportDate = date(r, "REPORT_DATE");
            if (reportDate == null) return Optional.empty();
            return Optional.of(new FinancialMetrics(
                    text(r, "REPORT_DATE_NAME"), reportDate, date(r, "NOTICE_DATE"), text(r, "ORG_TYPE"),
                    number(r, "EPSJB"), number(r, "EPSKCJB"), number(r, "MGJYXJJE"),
                    number(r, "TOTALOPERATEREVE"), number(r, "PARENTNETPROFIT"), number(r, "KCFJCXSYJLR"),
                    number(r, "TOTALOPERATEREVETZ"), number(r, "PARENTNETPROFITTZ"), number(r, "KCFJCXSYJLRTZ"),
                    number(r, "ROEJQ"), number(r, "XSMLL"), number(r, "ZCFZL")));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    static Double percentile(Double current, List<Double> samples) {
        if (current == null || current <= 0 || samples.size() < MIN_PERCENTILE_SAMPLES) return null;
        long atOrBelow = samples.stream().filter(value -> value <= current).count();
        return Math.round(atOrBelow * 1000.0 / samples.size()) / 10.0;
    }

    private static JsonNode rows(String response, ObjectMapper objectMapper) throws Exception {
        if (response == null || response.isBlank()) return null;
        JsonNode data = objectMapper.readTree(response).path("result").path("data");
        return data.isArray() && !data.isEmpty() ? data : null;
    }

    private static Double number(JsonNode row, String field) {
        JsonNode node = row.path(field);
        if (!node.isNumber()) return null;
        double value = node.asDouble();
        return Double.isFinite(value) ? value : null;
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static LocalDate date(JsonNode row, String field) {
        String value = text(row, field);
        try {
            return value == null || value.length() < 10 ? null : LocalDate.parse(value.substring(0, 10));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isAshareCode(String stockCode) {
        return stockCode != null && stockCode.matches("\\d{6}");
    }

    static String toSecuCode(String stockCode) {
        char first = stockCode.charAt(0);
        String market = first == '6' || first == '9' || first == '5' ? "SH" : first == '4' || first == '8' ? "BJ" : "SZ";
        return stockCode + "." + market;
    }
}
