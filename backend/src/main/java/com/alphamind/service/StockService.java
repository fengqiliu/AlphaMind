package com.alphamind.service;

import com.alphamind.model.dto.StockSearchResult;
import com.alphamind.model.dto.WeeklyStockRecommendation;
import com.alphamind.model.dto.WatchlistItem;
import com.alphamind.model.entity.WatchlistItemEntity;
import com.alphamind.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 股票服务 - 处理股票搜索和自选股管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private static final int WEEKLY_RECOMMENDATION_LIMIT = 3;
    private static final Map<String, Double> INDUSTRY_VALUE_WEIGHT = Map.ofEntries(
            Map.entry("银行", 0.95),
            Map.entry("保险", 0.92),
            Map.entry("电力", 0.90),
            Map.entry("食品", 0.88),
            Map.entry("家电", 0.85),
            Map.entry("医药", 0.84),
            Map.entry("石油", 0.80),
            Map.entry("建材", 0.76),
            Map.entry("汽车", 0.72),
            Map.entry("电子", 0.68),
            Map.entry("半导体", 0.63),
            Map.entry("人工智能", 0.58),
            Map.entry("软件", 0.56),
            Map.entry("互联网", 0.52),
            Map.entry("房地产", 0.70)
    );

    // 模拟股票数据库
    private static final Map<String, StockSearchResult> STOCK_DB = new ConcurrentHashMap<>();

    static {
        // ===== 白酒 =====
        add("600519", "贵州茅台",  "白酒", "上海主板", 1680.50,  1.53);
        add("000858", "五粮液",    "白酒", "深圳主板",  165.30,  0.82);
        add("000568", "泸州老窖",  "白酒", "深圳主板",  145.60,  1.25);
        add("000596", "古井贡酒",  "白酒", "深圳主板",  158.20,  0.55);
        add("600809", "山西汾酒",  "白酒", "上海主板",  185.40,  1.10);
        // ===== 银行 =====
        add("000001", "平安银行",  "银行", "深圳主板",   12.35, -0.45);
        add("600036", "招商银行",  "银行", "上海主板",   35.60,  1.25);
        add("601288", "农业银行",  "银行", "上海主板",    4.18,  0.24);
        add("601398", "工商银行",  "银行", "上海主板",    5.52,  0.18);
        add("601939", "建设银行",  "银行", "上海主板",    7.28,  0.28);
        add("600016", "民生银行",  "银行", "上海主板",    4.05, -0.25);
        add("601166", "兴业银行",  "银行", "上海主板",   17.35,  0.58);
        // ===== 保险/金融 =====
        add("601318", "中国平安",  "保险", "上海主板",   48.25,  0.68);
        add("601601", "中国太保",  "保险", "上海主板",   28.50,  0.35);
        add("601628", "中国人寿",  "保险", "上海主板",   36.80,  0.55);
        add("600030", "中信证券",  "券商", "上海主板",   21.50,  1.05);
        add("000776", "广发证券",  "券商", "深圳主板",   16.40,  0.62);
        // ===== 家电/消费 =====
        add("000333", "美的集团",  "家电", "深圳主板",   62.80, -0.35);
        add("000651", "格力电器",  "家电", "深圳主板",   38.50, -0.26);
        add("002415", "海康威视",  "电子", "深圳主板",   26.80,  0.75);
        add("600690", "海尔智家",  "家电", "上海主板",   24.60,  0.82);
        // ===== 汽车/新能源 =====
        add("002594", "比亚迪",    "汽车", "深圳主板",  238.50,  2.15);
        add("300750", "宁德时代",  "新能源","创业板",   185.60,  3.25);
        add("601238", "广汽集团",  "汽车", "上海主板",    7.80, -0.51);
        add("600104", "上汽集团",  "汽车", "上海主板",   14.20, -0.35);
        add("601127", "小康股份",  "汽车", "上海主板",   28.50,  1.75);
        // ===== 医药 =====
        add("600276", "恒瑞医药",  "医药", "上海主板",   52.30, -1.20);
        add("600196", "复星医药",  "医药", "上海主板",   28.60, -0.35);
        add("300015", "爱尔眼科",  "医疗", "创业板",    16.80,  0.60);
        add("000661", "长春高新",  "医药", "深圳主板",   88.50, -2.10);
        add("002007", "华兰生物",  "医药", "深圳主板",   18.50,  0.27);
        // ===== 半导体/科技 =====
        add("688981", "中芯国际",  "半导体","科创板",    52.80, -2.35);
        add("603986", "兆易创新",  "半导体","上海主板",  88.50, -1.20);
        add("688012", "中微公司",  "半导体","科创板",    88.60,  1.50);
        add("002475", "立讯精密",  "电子", "深圳主板",   25.60,  0.79);
        add("002027", "分众传媒",  "传媒", "深圳主板",    6.48,  0.31);
        // ===== 互联网/软件 =====
        add("300059", "东方财富",  "互联网","创业板",    18.60,  2.20);
        add("688036", "传音控股",  "电子", "科创板",    82.50,  1.85);
        add("002230", "科大讯飞",  "人工智能","深圳主板", 32.80, 3.10);
        add("688111", "金山办公",  "软件", "科创板",   106.50,  0.95);
        // ===== 房地产/建材 =====
        add("000002", "万科A",    "房地产","深圳主板",    7.32, -0.41);
        add("600048", "保利发展",  "房地产","上海主板",    9.80, -0.20);
        add("000786", "北新建材",  "建材", "深圳主板",   18.20,  0.55);
        // ===== 能源/化工 =====
        add("600028", "中国石化",  "石油", "上海主板",    5.82,  0.17);
        add("601857", "中国石油",  "石油", "上海主板",    7.85,  0.26);
        add("600547", "山东黄金",  "黄金", "上海主板",   28.50,  1.40);
        add("601088", "中国神华",  "煤炭", "上海主板",   38.60,  0.52);
        add("000895", "双汇发展",  "食品", "深圳主板",   24.50, -0.81);
        // ===== 钢铁/有色 =====
        add("600019", "宝山钢铁",  "钢铁", "上海主板",    6.25,  0.32);
        add("600362", "江西铜业",  "有色", "上海主板",   22.50,  0.90);
        add("002460", "赣锋锂业",  "有色", "深圳主板",   28.80,  2.10);
        // ===== 食品饮料 =====
        add("600887", "伊利股份",  "食品", "上海主板",   27.50,  0.55);
        add("002714", "牧原股份",  "农业", "深圳主板",   38.50, -1.04);
        add("300498", "温氏股份",  "农业", "创业板",    18.60, -0.54);
        // ===== 航空/交通 =====
        add("601111", "中国国航",  "航空", "上海主板",    7.38, -0.67);
        add("600115", "东方航空",  "航空", "上海主板",    5.82, -0.51);
        add("601808", "中海油服",  "石油服务","上海主板", 12.50,  0.81);
        // ===== 军工 =====
        add("600760", "中航沈飞",  "军工", "上海主板",   58.50,  1.72);
        add("000768", "中航西飞",  "军工", "深圳主板",   32.50,  0.93);
        // ===== 电力/公用事业 =====
        add("600900", "长江电力",  "电力", "上海主板",   28.50,  0.35);
        add("002380", "科远智慧",  "电力", "深圳主板",    8.80,  1.15);
    }

    private static void add(String code, String name, String industry, String market, double price, double changePct) {
        STOCK_DB.put(code, StockSearchResult.builder()
                .code(code).name(name).industry(industry).market(market)
                .currentPrice(price).changePercent(changePct).build());
    }

    private final WatchlistItemRepository watchlistItemRepository;

    /**
     * 搜索股票
     */
    public List<StockSearchResult> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String lowerQuery = query.toLowerCase();
        List<StockSearchResult> results = new ArrayList<>();

        STOCK_DB.forEach((code, stock) -> {
            if (code.contains(lowerQuery) ||
                    stock.getName().toLowerCase().contains(lowerQuery) ||
                    stock.getIndustry().toLowerCase().contains(lowerQuery)) {
                results.add(stock);
            }
        });

        return results;
    }

    /**
     * 获取股票详情
     */
    public Optional<StockSearchResult> getStock(String code) {
        return Optional.ofNullable(STOCK_DB.get(code));
    }

    /**
     * 获取每周低位价值股推荐。
     * 基于当前股票池的价格分位、行业均价折价、行业价值权重、板块稳定性与短期回撤情况进行综合评分。
     */
    public List<WeeklyStockRecommendation> getWeeklyValueRecommendations() {
        List<StockSearchResult> stocks = new ArrayList<>(STOCK_DB.values());
        if (stocks.isEmpty()) {
            return List.of();
        }

        double minPrice = stocks.stream()
                .mapToDouble(stock -> Optional.ofNullable(stock.getCurrentPrice()).orElse(0.0))
                .min()
                .orElse(0.0);
        double maxPrice = stocks.stream()
                .mapToDouble(stock -> Optional.ofNullable(stock.getCurrentPrice()).orElse(0.0))
                .max()
                .orElse(minPrice);

        Map<String, Double> industryAveragePrice = stocks.stream()
                .collect(Collectors.groupingBy(
                        StockSearchResult::getIndustry,
                        Collectors.averagingDouble(stock -> Optional.ofNullable(stock.getCurrentPrice()).orElse(0.0))
                ));

        String weekLabel = buildWeekLabel(LocalDate.now());
        List<WeeklyStockRecommendation> ranked = stocks.stream()
                .map(stock -> buildWeeklyRecommendation(stock, minPrice, maxPrice, industryAveragePrice, weekLabel))
                .sorted(Comparator.comparing(WeeklyStockRecommendation::getCompositeScore).reversed())
                .toList();

        List<WeeklyStockRecommendation> diversified = new ArrayList<>();
        Set<String> industries = new HashSet<>();
        for (WeeklyStockRecommendation recommendation : ranked) {
            if (industries.add(recommendation.getIndustry())) {
                diversified.add(recommendation);
            }
            if (diversified.size() == WEEKLY_RECOMMENDATION_LIMIT) {
                break;
            }
        }

        if (diversified.size() < WEEKLY_RECOMMENDATION_LIMIT) {
            for (WeeklyStockRecommendation recommendation : ranked) {
                boolean alreadyIncluded = diversified.stream()
                        .anyMatch(item -> Objects.equals(item.getStockCode(), recommendation.getStockCode()));
                if (!alreadyIncluded) {
                    diversified.add(recommendation);
                }
                if (diversified.size() == WEEKLY_RECOMMENDATION_LIMIT) {
                    break;
                }
            }
        }

        for (int i = 0; i < diversified.size(); i++) {
            diversified.get(i).setRank(i + 1);
        }
        return diversified;
    }

    private WeeklyStockRecommendation buildWeeklyRecommendation(
            StockSearchResult stock,
            double minPrice,
            double maxPrice,
            Map<String, Double> industryAveragePrice,
            String weekLabel) {

        double price = Optional.ofNullable(stock.getCurrentPrice()).orElse(0.0);
        double changePercent = Optional.ofNullable(stock.getChangePercent()).orElse(0.0);
        double industryAvg = Optional.ofNullable(industryAveragePrice.get(stock.getIndustry())).orElse(price);

        double lowPriceScore = 1.0 - normalize(price, minPrice, maxPrice);
        double industryDiscountScore = industryAvg <= 0
                ? 0.0
                : clamp((industryAvg - price) / industryAvg);
        double lowPositionScore = roundScore(lowPriceScore * 0.65 + industryDiscountScore * 0.35);

        double industryValueScore = Optional.ofNullable(INDUSTRY_VALUE_WEIGHT.get(stock.getIndustry())).orElse(0.60);
        double stabilityScore = switch (stock.getMarket()) {
            case "上海主板", "深圳主板" -> 0.92;
            case "创业板" -> 0.72;
            case "科创板" -> 0.68;
            default -> 0.75;
        };
        double pullbackScore = changePercent <= 0
                ? Math.min(Math.abs(changePercent) / 3.0, 1.0)
                : Math.max(0.12, 0.24 - Math.min(changePercent / 10.0, 0.18));
        double valueScore = roundScore(industryValueScore * 0.55 + stabilityScore * 0.25 + pullbackScore * 0.20);
        double compositeScore = roundScore(lowPositionScore * 0.55 + valueScore * 0.45);

        double discountPercent = industryAvg <= 0 ? 0.0 : Math.max(0.0, (industryAvg - price) / industryAvg * 100);
        String summary = String.format(
                Locale.ROOT,
                "%s当前价格位于样本偏低区间，兼具%s板块的价值属性与防守性，适合作为本周重点观察标的。",
                stock.getName(),
                stock.getIndustry()
        );

        List<String> highlights = List.of(
                String.format(Locale.ROOT, "低位评分 %.0f/100，价格处于股票池偏低分位", lowPositionScore * 100),
                String.format(Locale.ROOT, "较所属行业样本均价折价 %.1f%%", discountPercent),
                String.format(Locale.ROOT, "%s板块价值系数 %.0f/100，当前日涨跌幅 %.2f%%", stock.getIndustry(), industryValueScore * 100, changePercent)
        );

        return WeeklyStockRecommendation.builder()
                .weekLabel(weekLabel)
                .stockCode(stock.getCode())
                .stockName(stock.getName())
                .industry(stock.getIndustry())
                .market(stock.getMarket())
                .currentPrice(price)
                .changePercent(changePercent)
                .lowPositionScore(lowPositionScore)
                .valueScore(valueScore)
                .compositeScore(compositeScore)
                .summary(summary)
                .highlights(highlights)
                .build();
    }

    private String buildWeekLabel(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int weekYear = date.get(weekFields.weekBasedYear());
        return String.format(Locale.ROOT, "%d年第%02d周", weekYear, week);
    }

    private double normalize(double value, double min, double max) {
        if (Double.compare(max, min) == 0) {
            return 0.0;
        }
        return clamp((value - min) / (max - min));
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double roundScore(double value) {
        return Math.round(clamp(value) * 100.0) / 100.0;
    }

    /**
     * 添加自选股
     */
    @Transactional
    public void addToWatchlist(String userId, String stockCode) {
        if (watchlistItemRepository.existsByUserIdAndStockCode(userId, stockCode)) {
            log.info("用户 {} 的自选股 {} 已存在，跳过", userId, stockCode);
            return;
        }
        String stockName = Optional.ofNullable(STOCK_DB.get(stockCode))
                .map(StockSearchResult::getName).orElse(stockCode);
        WatchlistItemEntity entity = WatchlistItemEntity.builder()
                .userId(userId)
                .stockCode(stockCode)
                .stockName(stockName)
                .build();
        watchlistItemRepository.save(entity);
        log.info("用户 {} 添加自选股: {}", userId, stockCode);
    }

    /**
     * 移除自选股
     */
    @Transactional
    public void removeFromWatchlist(String userId, String stockCode) {
        int deleted = watchlistItemRepository.deleteByUserIdAndStockCode(userId, stockCode);
        if (deleted > 0) {
            log.info("用户 {} 移除自选股: {}", userId, stockCode);
        }
    }

    /**
     * 获取用户自选股列表
     */
    @Transactional(readOnly = true)
    public List<WatchlistItem> getWatchlist(String userId) {
        return watchlistItemRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entity -> {
                    StockSearchResult stock = STOCK_DB.get(entity.getStockCode());
                    double price = stock != null ? stock.getCurrentPrice() : 0.0;
                    double changePct = stock != null ? stock.getChangePercent() : 0.0;
                    return WatchlistItem.builder()
                            .stockCode(entity.getStockCode())
                            .stockName(entity.getStockName())
                            .addedAt(entity.getCreatedAt().toLocalDateTime())
                            .currentPrice(price)
                            .change(price * changePct / 100)
                            .changePercent(changePct)
                            .build();
                })
                .toList();
    }

    /**
     * 检查是否在自选股中
     */
    @Transactional(readOnly = true)
    public boolean isInWatchlist(String userId, String stockCode) {
        return watchlistItemRepository.existsByUserIdAndStockCode(userId, stockCode);
    }
}
