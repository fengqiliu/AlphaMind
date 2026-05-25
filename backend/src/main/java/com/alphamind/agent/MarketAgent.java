package com.alphamind.agent;

import com.alphamind.model.dto.*;
import com.alphamind.model.enums.AgentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 行情Agent - 负责采集和处理股票行情数据
 */
@Slf4j
@Component
public class MarketAgent extends BaseAgent {

    // 股票基础数据：[现价, 涨跌额, 涨跌幅, 开盘, 最高, 最低, PE, PB, 总市值, 换手率, 成交量]
    private static final Map<String, double[]> STOCK_DATA = new java.util.HashMap<>();
    private static final Map<String, String> STOCK_NAMES = new java.util.HashMap<>();

    static {
        // 白酒
        d("600519","贵州茅台", new double[]{1680.50,25.30, 1.53,1655.20,1692.00,1648.50, 35.8,12.5, 2110e9, 0.26, 3250000L});
        d("000858","五粮液",   new double[]{165.30, 1.35, 0.82, 163.90, 166.80, 162.50, 28.5, 7.2,  640e9, 1.25,52000000L});
        d("000568","泸州老窖", new double[]{145.60, 1.80, 1.25, 144.00, 147.20, 143.50, 30.2, 8.5,  220e9, 1.05,35000000L});
        d("000596","古井贡酒", new double[]{158.20, 0.87, 0.55, 157.50, 159.00, 156.80, 32.5, 9.2,   82e9, 0.85,12000000L});
        d("600809","山西汾酒", new double[]{185.40, 2.02, 1.10, 183.50, 186.80, 182.80, 38.2,11.5,  225e9, 0.95,28000000L});
        // 银行
        d("000001","平安银行", new double[]{12.35, -0.05,-0.40, 12.40,  12.52,  12.28,  6.5, 0.8,  239e9, 0.45,180000000L});
        d("600036","招商银行", new double[]{35.60,  0.44, 1.25, 35.20,  35.88,  35.05,  9.8, 1.5,  895e9, 0.38,120000000L});
        d("601288","农业银行", new double[]{4.18,   0.01, 0.24,  4.17,   4.20,   4.15,  5.2, 0.6, 1435e9, 0.20,850000000L});
        d("601398","工商银行", new double[]{5.52,   0.01, 0.18,  5.51,   5.55,   5.50,  5.8, 0.7, 1970e9, 0.15,620000000L});
        d("601939","建设银行", new double[]{7.28,   0.02, 0.28,  7.26,   7.31,   7.24,  5.5, 0.7, 1825e9, 0.18,540000000L});
        d("600016","民生银行", new double[]{4.05,  -0.01,-0.25,  4.06,   4.09,   4.03,  4.8, 0.5,  310e9, 0.22,260000000L});
        d("601166","兴业银行", new double[]{17.35,  0.10, 0.58, 17.25,  17.48,  17.20,  5.5, 0.8,  358e9, 0.30, 90000000L});
        // 保险/金融
        d("601318","中国平安", new double[]{48.25,  0.33, 0.68, 47.90,  48.55,  47.70, 10.2, 1.8,  878e9, 0.32, 96000000L});
        d("601601","中国太保", new double[]{28.50,  0.10, 0.35, 28.42,  28.68,  28.30, 12.5, 1.5,  260e9, 0.28, 42000000L});
        d("601628","中国人寿", new double[]{36.80,  0.20, 0.55, 36.60,  37.00,  36.50, 14.2, 2.1,  1040e9,0.25, 68000000L});
        d("600030","中信证券", new double[]{21.50,  0.22, 1.05, 21.30,  21.72,  21.20, 18.5, 1.8,  312e9, 0.68, 85000000L});
        d("000776","广发证券", new double[]{16.40,  0.10, 0.62, 16.30,  16.55,  16.22, 16.2, 1.5,  198e9, 0.55, 62000000L});
        // 家电
        d("000333","美的集团", new double[]{62.80, -0.22,-0.35, 63.00,  63.45,  62.50, 18.5, 3.5,  440e9, 0.55, 72000000L});
        d("000651","格力电器", new double[]{38.50, -0.10,-0.26, 38.62,  38.80,  38.30, 10.2, 2.5,  218e9, 0.45, 58000000L});
        d("002415","海康威视", new double[]{26.80,  0.20, 0.75, 26.60,  26.98,  26.50, 22.5, 3.8,  255e9, 0.62, 95000000L});
        d("600690","海尔智家", new double[]{24.60,  0.20, 0.82, 24.42,  24.82,  24.35, 20.5, 2.8,  238e9, 0.58, 78000000L});
        // 汽车/新能源
        d("002594","比亚迪",   new double[]{238.50, 5.02, 2.15,233.50, 241.80, 232.00, 55.0,11.2,  690e9, 1.85, 88000000L});
        d("300750","宁德时代", new double[]{185.60, 5.82, 3.25,180.00, 188.50, 179.50, 35.2, 8.5,  815e9, 2.25,110000000L});
        d("601238","广汽集团", new double[]{7.80,  -0.04,-0.51,  7.84,   7.88,   7.76, 10.5, 0.9,   78e9, 0.42, 65000000L});
        d("600104","上汽集团", new double[]{14.20, -0.05,-0.35, 14.26,  14.35,  14.12,  8.5, 1.0,  135e9, 0.35, 82000000L});
        // 医药
        d("600276","恒瑞医药", new double[]{52.30, -0.64,-1.20, 52.90,  53.10,  52.00, 42.5, 5.8,  525e9, 0.72, 45000000L});
        d("600196","复星医药", new double[]{28.60, -0.10,-0.35, 28.72,  28.85,  28.50, 20.5, 2.5,  192e9, 0.52, 32000000L});
        d("300015","爱尔眼科", new double[]{16.80,  0.10, 0.60, 16.70,  16.92,  16.65, 35.5, 6.5,  280e9, 0.85, 42000000L});
        d("000661","长春高新", new double[]{88.50, -1.89,-2.10, 90.30,  90.50,  88.00, 18.5, 4.5,  112e9, 1.25, 15000000L});
        // 半导体
        d("688981","中芯国际", new double[]{52.80, -1.28,-2.35, 54.00,  54.20,  52.50,120.5, 4.2,  430e9, 3.15, 95000000L});
        d("603986","兆易创新", new double[]{88.50, -1.08,-1.20, 89.60,  89.80,  88.20, 65.2, 8.5,   96e9, 1.85, 28000000L});
        d("688012","中微公司", new double[]{88.60,  1.30, 1.50, 87.50,  89.30,  87.20,120.5,15.2,  120e9, 2.25, 32000000L});
        d("002475","立讯精密", new double[]{25.60,  0.20, 0.79, 25.42,  25.80,  25.30, 22.5, 4.5,  185e9, 0.95, 68000000L});
        // 互联网/软件
        d("300059","东方财富", new double[]{18.60,  0.40, 2.20, 18.22,  18.78,  18.18, 38.5, 5.8,  242e9, 1.85,125000000L});
        d("002230","科大讯飞", new double[]{32.80,  0.99, 3.10, 31.85,  33.10,  31.80,120.5,12.5,  245e9, 2.85, 85000000L});
        d("688111","金山办公", new double[]{106.50, 1.00, 0.95,105.52, 107.20, 105.20, 65.2,18.5,  132e9, 0.78, 22000000L});
        // 房地产
        d("000002","万科A",    new double[]{7.32,  -0.03,-0.41,  7.35,   7.40,   7.28,  6.5, 0.7,   86e9, 0.48,125000000L});
        d("600048","保利发展", new double[]{9.80,  -0.02,-0.20,  9.82,   9.88,   9.76,  8.5, 0.9,  145e9, 0.35, 95000000L});
        // 能源/化工
        d("600028","中国石化", new double[]{5.82,   0.01, 0.17,  5.81,   5.85,   5.79,  9.5, 0.7,  699e9, 0.28,480000000L});
        d("601857","中国石油", new double[]{7.85,   0.02, 0.26,  7.83,   7.88,   7.80, 12.5, 1.0, 1438e9, 0.22,380000000L});
        d("600547","山东黄金", new double[]{28.50,  0.40, 1.40, 28.12,  28.72,  28.05, 45.2, 2.8,   88e9, 1.25, 32000000L});
        d("601088","中国神华", new double[]{38.60,  0.20, 0.52, 38.42,  38.80,  38.32, 12.5, 2.2,  775e9, 0.35, 95000000L});
        // 钢铁/有色
        d("600019","宝山钢铁", new double[]{6.25,   0.02, 0.32,  6.23,   6.28,   6.20,  8.5, 0.7,  218e9, 0.42,185000000L});
        d("002460","赣锋锂业", new double[]{28.80,  0.59, 2.10, 28.25,  29.10,  28.18, 35.2, 2.5,  240e9, 2.85, 88000000L});
        // 食品饮料
        d("600887","伊利股份", new double[]{27.50,  0.15, 0.55, 27.38,  27.65,  27.28, 20.5, 4.5,  168e9, 0.68, 62000000L});
        d("000895","双汇发展", new double[]{24.50, -0.20,-0.81, 24.70,  24.75,  24.42, 15.5, 3.8,   90e9, 0.52, 32000000L});
        // 航空
        d("601111","中国国航", new double[]{7.38,  -0.05,-0.67,  7.43,   7.48,   7.33, 18.5, 1.2,  115e9, 0.62, 82000000L});
        d("600115","东方航空", new double[]{5.82,  -0.03,-0.51,  5.85,   5.88,   5.78, 22.5, 1.0,   88e9, 0.55, 95000000L});
        // 军工
        d("600760","中航沈飞", new double[]{58.50,  0.99, 1.72, 57.55,  58.88,  57.45,155.5,15.2,  380e9, 0.85, 18000000L});
        // 电力
        d("600900","长江电力", new double[]{28.50,  0.10, 0.35, 28.42,  28.62,  28.35, 22.5, 3.5,  540e9, 0.28, 95000000L});
    }

    private static void d(String code, String name, double[] data) {
        STOCK_DATA.put(code, data);
        STOCK_NAMES.put(code, name);
    }

    @Value("${alphamind.market.fetch-real-data:false}")
    private boolean fetchRealData;

    @Value("${alphamind.market.kline-days:120}")
    private int klineDays;

    private static final ObjectMapper KLINE_MAPPER = new ObjectMapper();

    private static final RestTemplate REST_TEMPLATE;
    static {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        REST_TEMPLATE = new RestTemplate(factory);
    }

    /** Sina Finance 行情记录 */
    private record SinaQuote(String name, double open, double prevClose, double current,
                             double high, double low, long volumeLots, double amount) {}

    /** 东方财富历史 K 线原始记录（前复权日K）*/
    private record EmKlineRecord(String date, double open, double close,
                                 double high, double low, long volume) {}

    /** 将 A 股代码转换为东方财富 secid（1=SH, 0=SZ/BJ）*/
    private String toEastMoneySecid(String code) {
        if (code.startsWith("6") || code.startsWith("9")) return "1." + code;
        return "0." + code;
    }

    /**
     * 调用东方财富历史 K 线接口，返回前复权日K 列表（按时间升序）。
     * 失败或数据为空时返回空 List，调用方降级为生成式数据。
     */
    private List<EmKlineRecord> fetchKlineFromEastMoney(String stockCode, int days) {
        try {
            String secid = toEastMoneySecid(stockCode);
            // fields2: f51=date f52=open f53=close f54=high f55=low f56=volume(手)
            String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                    + "?secid=" + secid
                    + "&fields1=f1,f2,f3,f4,f5,f6"
                    + "&fields2=f51,f52,f53,f54,f55,f56"
                    + "&klt=101&fqt=1&end=20500101"
                    + "&lmt=" + Math.max(days, 60);
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (compatible; AlphaMind/1.0)");
            ResponseEntity<String> resp = REST_TEMPLATE.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (resp.getBody() == null) return List.of();

            JsonNode klines = KLINE_MAPPER.readTree(resp.getBody())
                    .path("data").path("klines");
            if (!klines.isArray() || klines.isEmpty()) return List.of();

            List<EmKlineRecord> result = new ArrayList<>();
            for (JsonNode node : klines) {
                String[] p = node.asText().split(",");
                if (p.length < 6) continue;
                try {
                    result.add(new EmKlineRecord(
                            p[0],
                            Double.parseDouble(p[1]),           // open
                            Double.parseDouble(p[2]),           // close
                            Double.parseDouble(p[3]),           // high
                            Double.parseDouble(p[4]),           // low
                            (long)(Double.parseDouble(p[5]) * 100) // 手 → 股
                    ));
                } catch (NumberFormatException ignored) {}
            }
            log.debug("EastMoney K-line: {} records for {}", result.size(), stockCode);
            return result;
        } catch (Exception e) {
            log.debug("EastMoney K-line unavailable for {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    public MarketAgent() {
        super(AgentType.MARKET);
    }

    /**
     * 将 A 股代码转换为新浪行情 symbol（sh/sz/bj 前缀）
     */
    private String toSinaSymbol(String code) {
        if (code.startsWith("6") || code.startsWith("9")) return "sh" + code;
        if (code.startsWith("0") || code.startsWith("2") || code.startsWith("3")) return "sz" + code;
        if (code.startsWith("8") || code.startsWith("4")) return "bj" + code;
        return "sz" + code;
    }

    /**
     * 调用新浪行情 API 获取实时报价，失败返回 empty。
     * 响应示例：var hq_str_sh600519="贵州茅台,open,prevClose,current,high,low,…,volLots,amount,…";
     */
    private Optional<SinaQuote> fetchFromSina(String stockCode) {
        try {
            String url = "https://hq.sinajs.cn/list=" + toSinaSymbol(stockCode);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://finance.sina.com.cn");
            headers.set("User-Agent", "Mozilla/5.0 (compatible; AlphaMind/1.0)");
            ResponseEntity<byte[]> resp = REST_TEMPLATE.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            if (resp.getBody() == null) return Optional.empty();

            String body = new String(resp.getBody(), Charset.forName("GBK"));
            int start = body.indexOf('"') + 1;
            int end   = body.lastIndexOf('"');
            if (start < 1 || end <= start) return Optional.empty();

            String[] p = body.substring(start, end).split(",");
            if (p.length < 10) return Optional.empty();
            double current = Double.parseDouble(p[3]);
            if (current <= 0) return Optional.empty(); // 停牌或休市

            return Optional.of(new SinaQuote(
                    p[0],
                    Double.parseDouble(p[1]),   // open
                    Double.parseDouble(p[2]),   // prevClose
                    current,
                    Double.parseDouble(p[4]),   // high
                    Double.parseDouble(p[5]),   // low
                    Long.parseLong(p[8]),        // volumeLots
                    Double.parseDouble(p[9])     // amount
            ));
        } catch (Exception e) {
            log.debug("Sina API unavailable for {}: {}", stockCode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public AnalysisReportDTO analyze(AnalysisReportDTO report) {
        logInfo("开始采集行情数据: " + report.getStockCode());

        try {
            MarketDataDTO marketData = fetchMarketData(report.getStockCode(), report.getStockName());

            // 调用LLM生成行情分析摘要
            String aiSummary = generateMarketSummary(marketData);
            marketData.setAiSummary(aiSummary);

            report.setMarketData(marketData);
            setContext("marketData", marketData);

            logInfo("行情数据采集完成，当前价格: " + marketData.getCurrentPrice());
            return report;
        } catch (Exception e) {
            logError("行情数据采集失败", e);
            throw new RuntimeException("行情数据采集失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatMessage chat(ChatMessage userMessage) {
        MarketDataDTO marketData = getContext("marketData");
        if (marketData == null) {
            return buildErrorMessage("暂无行情数据，请先发起分析。");
        }

        // 优先使用LLM生成回复
        String response = llmCall(getSystemPrompt(),
                buildMarketPrompt(marketData, userMessage.getContent()));

        if (response == null) {
            // 降级为模板响应
            response = buildMarketTemplateResponse(marketData);
        }

        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(response)
                .agentType(agentType)
                .agentName(agentType.getName())
                .modelUsed(isLlmAvailable() ? "AI" : "template")
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一名专业的股票行情分析师，负责采集和解读股票市场的实时行情数据。

            你的职责：
            1. 解读股票的实时价格、涨跌幅、成交量等基础数据
            2. 分析开盘价、最高价、最低价的关系判断当日走势
            3. 解读市盈率、市净率等估值指标
            4. 结合市值和换手率判断市场活跃度
            5. 用简洁专业的语言向用户解释行情数据

            回答要求：
            - 客观准确，给出数据分析结论
            - 数据异常时需要特别标注
            - 涉及专业术语时简要解释
            """;
    }

    private String generateMarketSummary(MarketDataDTO data) {
        String prompt = String.format(
                "请用50字以内简要分析 %s(%s) 的行情：当前价¥%.2f，涨跌%+.2f%%，PE:%.1f，换手率%.2f%%",
                data.getStockName(), data.getStockCode(), data.getCurrentPrice(),
                data.getChangePercent(), data.getPe(), data.getTurnoverRate());
        String result = llmCall(getSystemPrompt(), prompt);
        return result != null ? result : buildDefaultSummary(data);
    }

    private String buildDefaultSummary(MarketDataDTO data) {
        return String.format("%s 当前报价¥%.2f，%s%.2f%%。PE %.1f，换手率%.2f%%。",
                data.getStockName(), data.getCurrentPrice(),
                data.getChangePercent() >= 0 ? "上涨" : "下跌",
                Math.abs(data.getChangePercent()),
                data.getPe(), data.getTurnoverRate());
    }

    private String buildMarketPrompt(MarketDataDTO data, String userQuestion) {
        return String.format("""
                股票行情数据：
                - 股票：%s (%s)
                - 当前价：¥%.2f，涨跌：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万手，换手率：%.2f%%
                - 市盈率(PE)：%.2f，市净率(PB)：%.2f
                - 总市值：%.2f亿

                用户问题：%s
                """,
                data.getStockName(), data.getStockCode(),
                data.getCurrentPrice(), data.getChangePercent(), data.getChange(),
                data.getOpen(), data.getHigh(), data.getLow(),
                data.getVolume() / 10000.0, data.getTurnoverRate(),
                data.getPe(), data.getPb(), data.getMarketCap() / 1e8,
                userQuestion);
    }

    private String buildMarketTemplateResponse(MarketDataDTO data) {
        return String.format("""
                **%s (%s) 行情数据**

                - 当前价格：¥%.2f
                - 涨跌幅：%+.2f%% (¥%+.2f)
                - 开盘：¥%.2f | 最高：¥%.2f | 最低：¥%.2f
                - 成交量：%.2f万手 | 换手率：%.2f%%
                - 市盈率(PE)：%.2f | 市净率(PB)：%.2f
                - 总市值：%.2f亿

                数据更新时间：%s
                """,
                data.getStockName(), data.getStockCode(),
                data.getCurrentPrice(), data.getChangePercent(), data.getChange(),
                data.getOpen(), data.getHigh(), data.getLow(),
                data.getVolume() / 10000.0, data.getTurnoverRate(),
                data.getPe(), data.getPb(), data.getMarketCap() / 1e8,
                data.getUpdateTime());
    }

    private ChatMessage buildErrorMessage(String content) {
        return ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role("assistant")
                .content(content)
                .agentType(agentType)
                .agentName(agentType.getName())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * 获取市场数据。
     * 当 alphamind.market.fetch-real-data=true 时优先调用新浪行情 API 获取实时价格；
     * API 不可用或关闭时降级为静态 STOCK_DATA（PE/PB/市值/换手率始终来自静态表）。
     */
    private MarketDataDTO fetchMarketData(String stockCode, String stockNameHint) {
        double[] baseData = STOCK_DATA.get(stockCode);
        String stockName = STOCK_NAMES.getOrDefault(stockCode,
                stockNameHint != null ? stockNameHint : stockCode);

        double basePrice, change, changePct, open, high, low, pe, pb, marketCap, turnover;
        long volume;

        if (baseData != null) {
            basePrice = baseData[0];
            change    = baseData[1];
            changePct = baseData[2];
            open      = baseData[3];
            high      = baseData[4];
            low       = baseData[5];
            pe        = baseData[6];
            pb        = baseData[7];
            marketCap = baseData[8];
            turnover  = baseData[9];
            volume    = (long) baseData[10];
        } else {
            // 未收录股票 - 中性默认数据
            basePrice = 50.0;
            change    = 0;
            changePct = 0;
            open      = 50.0;
            high      = 51.0;
            low       = 49.0;
            pe        = 20.0;
            pb        = 2.0;
            marketCap = 5e10;
            turnover  = 1.0;
            volume    = 5_000_000L;
        }

        // ── 优先使用新浪行情 API 获取实时价格 ──
        if (fetchRealData) {
            Optional<SinaQuote> realQuote = fetchFromSina(stockCode);
            if (realQuote.isPresent()) {
                SinaQuote q = realQuote.get();
                basePrice = q.current();
                open      = q.open();
                high      = q.high();
                low       = q.low();
                change    = Math.round((q.current() - q.prevClose()) * 100) / 100.0;
                changePct = q.prevClose() > 0
                        ? Math.round(change / q.prevClose() * 10000) / 100.0 : 0;
                volume    = q.volumeLots() * 100; // 手 → 股
                // 用真实成交量和静态总股本反算换手率
                if (baseData != null && baseData[0] > 0) {
                    long totalShares = (long) (baseData[8] / baseData[0]);
                    if (totalShares > 0) {
                        turnover = Math.round(volume * 10000.0 / totalShares) / 100.0;
                    }
                    marketCap = (long) (basePrice * totalShares);
                }
                if (!q.name().isBlank()) stockName = q.name();
                log.debug("Sina real-time: {} price={} change={}%", stockCode, basePrice, changePct);
            } else {
                log.debug("Sina API miss for {}, using static data", stockCode);
            }
        }

        // ── K 线数据（优先使用真实历史数据）──────────────────────────────────
        List<String> klineDatesList;
        List<double[]> klineData;
        List<Long> klineVolumesList;

        if (fetchRealData) {
            List<EmKlineRecord> realKlines = fetchKlineFromEastMoney(stockCode, klineDays);
            if (!realKlines.isEmpty()) {
                klineDatesList  = realKlines.stream().map(EmKlineRecord::date).collect(Collectors.toList());
                klineData       = realKlines.stream()
                        .map(r -> new double[]{r.open(), r.close(), r.low(), r.high()})
                        .collect(Collectors.toList());
                klineVolumesList = realKlines.stream().map(EmKlineRecord::volume).collect(Collectors.toList());
                // 以真实K线末条收盘价修正当日价格（若 Sina 实时价未能覆盖）
                double lastClose = realKlines.get(realKlines.size() - 1).close();
                if (basePrice == STOCK_DATA.getOrDefault(stockCode, new double[]{0})[0]) {
                    basePrice = lastClose;
                }
                log.info("Real K-line loaded for {}: {} days, last close={}", stockCode,
                        realKlines.size(), lastClose);
            } else {
                log.debug("EastMoney K-line empty for {}, using generated data", stockCode);
                klineData        = generateKlineData(basePrice, klineDays);
                klineDatesList   = generateKlineDates(klineDays);
                klineVolumesList = generateKlineVolumes(volume, klineDays);
            }
        } else {
            klineData        = generateKlineData(basePrice, klineDays);
            klineDatesList   = generateKlineDates(klineDays);
            klineVolumesList = generateKlineVolumes(volume, klineDays);
        }

        return MarketDataDTO.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(Math.round(basePrice * 100) / 100.0)
                .change(Math.round(change * 100) / 100.0)
                .changePercent(Math.round(changePct * 100) / 100.0)
                .open(Math.round(open * 100) / 100.0)
                .high(Math.round(high * 100) / 100.0)
                .low(Math.round(low * 100) / 100.0)
                .volume(volume)
                .amount(Math.round(basePrice * volume / 1e7) * 1e7)
                .turnoverRate(Math.round(turnover * 100) / 100.0)
                .pe(Math.round(pe * 10) / 10.0)
                .pb(Math.round(pb * 10) / 10.0)
                .marketCap((long) marketCap)
                .updateTime(java.time.LocalDateTime.now().toString())
                .klineDates(klineDatesList)
                .klines(klineData)
                .klineVolumes(klineVolumesList)
                .ma5(calculateMA(klineData, 5))
                .ma10(calculateMA(klineData, 10))
                .ma20(calculateMA(klineData, 20))
                .ma60(calculateMA(klineData, 60))
                .build();
    }

    /** 生成近 N 个交易日日期列表 */
    private List<String> generateKlineDates(int days) {
        List<String> dates = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.now();
        int count = 0;
        while (count < days) {
            // 跳过周末
            if (date.getDayOfWeek().getValue() < 6) {
                dates.add(0, date.format(fmt));
                count++;
            }
            date = date.minusDays(1);
        }
        return dates;
    }

    /** 生成模拟 K 线 [open, close, low, high] */
    private List<double[]> generateKlineData(double basePrice, int days) {
        List<double[]> klines = new ArrayList<>();
        double price = basePrice * (1 - 0.05); // 从稍低价格开始形成趋势
        for (int i = 0; i < days; i++) {
            double pctChange = (Math.random() - 0.48) * 0.04; // 轻微向上偏移
            double open  = Math.round(price * 100) / 100.0;
            double close = Math.round(price * (1 + pctChange) * 100) / 100.0;
            double high  = Math.round(Math.max(open, close) * (1 + Math.random() * 0.015) * 100) / 100.0;
            double low   = Math.round(Math.min(open, close) * (1 - Math.random() * 0.015) * 100) / 100.0;
            klines.add(new double[]{open, close, low, high});
            price = close;
        }
        return klines;
    }

    /** 生成模拟成交量 */
    private List<Long> generateKlineVolumes(long baseVolume, int days) {
        List<Long> volumes = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            volumes.add((long)(baseVolume * (0.5 + Math.random())));
        }
        return volumes;
    }

    /** 计算均线 */
    private List<Double> calculateMA(List<double[]> klines, int period) {
        List<Double> ma = new ArrayList<>();
        for (int i = 0; i < klines.size(); i++) {
            if (i < period - 1) {
                ma.add(null);
                continue;
            }
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += klines.get(j)[1]; // close price
            }
            ma.add(Math.round(sum / period * 100) / 100.0);
        }
        return ma;
    }
}
