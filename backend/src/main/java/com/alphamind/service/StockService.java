package com.alphamind.service;

import com.alphamind.market.service.QuoteResolution;
import com.alphamind.market.service.StockQuoteService;
import com.alphamind.model.dto.StockSearchResult;
import com.alphamind.model.dto.WeeklyStockRecommendation;
import com.alphamind.model.dto.WatchlistItem;
import com.alphamind.model.entity.WatchlistItemEntity;
import com.alphamind.repository.WatchlistItemRepository;
import com.alphamind.service.StockCatalog.StockMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 股票服务 - 处理股票搜索、周荐评分和自选股管理。
 *
 * <p>身份信息（代码/名称/行业/板块）来自 {@link StockCatalog}，价格一律来自
 * {@link StockQuoteService}。服务本身不保存任何价格常量——写死的价格会让下游把
 * "永不变化的数字"当成实时行情，进而算出看似精确、实则虚构的评分。
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

    private final WatchlistItemRepository watchlistItemRepository;
    private final StockCatalog stockCatalog;
    private final StockQuoteService stockQuoteService;

    /**
     * 搜索股票。命中的股票会批量补齐实时价格；拿不到行情的条目仍然返回，
     * 但价格字段保持为 null，由前端显示为"—"而不是 0。
     */
    public List<StockSearchResult> searchStocks(String query) {
        List<StockMetadata> matches = stockCatalog.search(query);
        if (matches.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, QuoteResolution> quotes = stockQuoteService.quotes(
                matches.stream().map(StockMetadata::code).toList());

        return matches.stream()
                .map(meta -> toSearchResult(meta, quotes.get(meta.code())))
                .toList();
    }

    /**
     * 获取股票详情
     */
    public Optional<StockSearchResult> getStock(String code) {
        return stockCatalog.find(code)
                .map(meta -> toSearchResult(meta, stockQuoteService.quote(code).orElse(null)));
    }

    private StockSearchResult toSearchResult(StockMetadata meta, QuoteResolution resolution) {
        StockSearchResult.StockSearchResultBuilder builder = StockSearchResult.builder()
                .code(meta.code())
                .name(meta.name())
                .industry(meta.industry())
                .market(meta.market());

        if (resolution != null) {
            builder.currentPrice(resolution.snapshot().quote().current())
                    .changePercent(resolution.snapshot().changePercent())
                    .dataSource(resolution.sourceName())
                    .stale(resolution.stale());
        }
        return builder.build();
    }

    /**
     * 获取每周低位价值股推荐。
     * 基于当前股票池的价格分位、行业均价折价、行业价值权重、板块稳定性与短期回撤情况进行综合评分。
     *
     * <p>只有拿到真实报价的股票才参与评分。行情源完全不可用时返回空列表——
     * 宁可不给推荐，也不能用虚构价格算出"低位评分 87/100"这种精确到小数点的结论。
     */
    public List<WeeklyStockRecommendation> getWeeklyValueRecommendations() {
        Collection<StockMetadata> pool = stockCatalog.all();
        Map<String, QuoteResolution> quotes = stockQuoteService.quotes(
                pool.stream().map(StockMetadata::code).toList());

        List<PricedStock> priced = pool.stream()
                .map(meta -> PricedStock.of(meta, quotes.get(meta.code())))
                .flatMap(Optional::stream)
                .toList();

        if (priced.isEmpty()) {
            log.warn("行情源不可用，本周低位价值股推荐返回空列表（不使用虚构价格评分）");
            return List.of();
        }

        double minPrice = priced.stream().mapToDouble(PricedStock::price).min().orElse(0.0);
        double maxPrice = priced.stream().mapToDouble(PricedStock::price).max().orElse(minPrice);

        Map<String, Double> industryAveragePrice = priced.stream()
                .collect(Collectors.groupingBy(
                        stock -> stock.meta().industry(),
                        Collectors.averagingDouble(PricedStock::price)
                ));

        String weekLabel = buildWeekLabel(LocalDate.now());
        List<WeeklyStockRecommendation> ranked = priced.stream()
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
            PricedStock stock,
            double minPrice,
            double maxPrice,
            Map<String, Double> industryAveragePrice,
            String weekLabel) {

        double price = stock.price();
        double changePercent = stock.changePercent();
        String industry = stock.meta().industry();
        double industryAvg = Optional.ofNullable(industryAveragePrice.get(industry)).orElse(price);

        double lowPriceScore = 1.0 - normalize(price, minPrice, maxPrice);
        double industryDiscountScore = industryAvg <= 0
                ? 0.0
                : clamp((industryAvg - price) / industryAvg);
        double lowPositionScore = roundScore(lowPriceScore * 0.65 + industryDiscountScore * 0.35);

        double industryValueScore = Optional.ofNullable(INDUSTRY_VALUE_WEIGHT.get(industry)).orElse(0.60);
        double stabilityScore = switch (stock.meta().market()) {
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
                stock.meta().name(),
                industry
        );

        List<String> highlights = List.of(
                String.format(Locale.ROOT, "低位评分 %.0f/100，价格处于股票池偏低分位", lowPositionScore * 100),
                String.format(Locale.ROOT, "较所属行业样本均价折价 %.1f%%", discountPercent),
                String.format(Locale.ROOT, "%s板块价值系数 %.0f/100，当前日涨跌幅 %.2f%%", industry, industryValueScore * 100, changePercent)
        );

        return WeeklyStockRecommendation.builder()
                .weekLabel(weekLabel)
                .stockCode(stock.meta().code())
                .stockName(stock.meta().name())
                .industry(industry)
                .market(stock.meta().market())
                .currentPrice(price)
                .changePercent(changePercent)
                .lowPositionScore(lowPositionScore)
                .valueScore(valueScore)
                .compositeScore(compositeScore)
                .summary(summary)
                .highlights(highlights)
                .dataSource(stock.dataSource())
                .stale(stock.stale())
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
        String stockName = stockCatalog.find(stockCode).map(StockMetadata::name).orElse(stockCode);
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
     * 获取用户自选股列表。价格批量拉取；拿不到行情的条目价格字段保持为 null。
     */
    @Transactional(readOnly = true)
    public List<WatchlistItem> getWatchlist(String userId) {
        List<WatchlistItemEntity> entities = watchlistItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (entities.isEmpty()) {
            return List.of();
        }

        Map<String, QuoteResolution> quotes = stockQuoteService.quotes(
                entities.stream().map(WatchlistItemEntity::getStockCode).toList());

        return entities.stream()
                .map(entity -> {
                    WatchlistItem.WatchlistItemBuilder builder = WatchlistItem.builder()
                            .stockCode(entity.getStockCode())
                            .stockName(entity.getStockName())
                            .addedAt(entity.getCreatedAt().toLocalDateTime());

                    QuoteResolution resolution = quotes.get(entity.getStockCode());
                    if (resolution != null) {
                        double price = resolution.snapshot().quote().current();
                        double previousClose = resolution.snapshot().quote().previousClose();
                        builder.currentPrice(price)
                                .change(previousClose > 0 ? round(price - previousClose) : null)
                                .changePercent(resolution.snapshot().changePercent())
                                .dataSource(resolution.sourceName())
                                .stale(resolution.stale());
                    }
                    return builder.build();
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

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 参与周荐评分的股票：元数据 + 一条真实报价。
     * 只有价格与涨跌幅都可得时才构造得出，因此评分逻辑不需要再处理缺失值。
     */
    private record PricedStock(StockMetadata meta, double price, double changePercent,
                               String dataSource, boolean stale) {

        static Optional<PricedStock> of(StockMetadata meta, QuoteResolution resolution) {
            if (resolution == null) {
                return Optional.empty();
            }
            Double changePercent = resolution.snapshot().changePercent();
            double price = resolution.snapshot().quote().current();
            if (changePercent == null || price <= 0) {
                return Optional.empty();
            }
            return Optional.of(new PricedStock(
                    meta, price, changePercent, resolution.sourceName(), resolution.stale()));
        }
    }
}
