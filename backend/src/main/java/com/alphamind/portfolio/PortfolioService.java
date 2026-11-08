package com.alphamind.portfolio;

import com.alphamind.config.ConcurrencyConfig;
import com.alphamind.investment.PersonalModelCatalog;
import com.alphamind.investment.PersonalTimingCalculator;
import com.alphamind.market.service.MarketDataMapper;
import com.alphamind.service.AnalysisHistoryService;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.ResilientMarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 当前组合的读写，以及按组合推算个人模型 Gate 5 的交易后数值。 */
@Slf4j
@Service
public class PortfolioService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final PortfolioRepository repository;
    private final PersonalModelCatalog catalog;
    private final ResilientMarketDataService marketData;
    private final ConcurrencyConfig.IoExecutor io;
    private final AnalysisHistoryService history;
    private final PortfolioSnapshotRepository snapshots;

    public PortfolioService(PortfolioRepository repository, PersonalModelCatalog catalog,
                            ResilientMarketDataService marketData, ConcurrencyConfig.IoExecutor io,
                            AnalysisHistoryService history, PortfolioSnapshotRepository snapshots) {
        this.repository = repository;
        this.catalog = catalog;
        this.marketData = marketData;
        this.io = io;
        this.history = history;
        this.snapshots = snapshots;
    }

    public Optional<PortfolioProfile> get() {
        return repository.findById(PortfolioEntity.SINGLETON_ID).map(PortfolioEntity::toProfile);
    }

    /** 整体覆盖保存。模型内标的的名称与主题以模型为准；同一代码不能重复录入。 */
    @Transactional
    public PortfolioProfile save(PortfolioProfile input) {
        Set<String> codes = new HashSet<>();
        List<Holding> holdings = new ArrayList<>();
        for (Holding holding : input.holdings()) {
            if (!holding.pricedByMarket() && holding.manualValue() == null) {
                throw new IllegalArgumentException(holding.name() + "：请填写代码和股数，或填写手工市值");
            }
            if (holding.stockCode() != null && !codes.add(holding.stockCode())) {
                throw new IllegalArgumentException("代码 " + holding.stockCode() + " 重复录入，请合并为一行");
            }
            holdings.add(normalize(holding));
        }
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID, holdings, input.cash(), input.lowVolatility(),
                input.peakValue(), OffsetDateTime.now(MARKET_ZONE));
        return repository.save(entity).toProfile();
    }

    private Holding normalize(Holding holding) {
        if (holding.stockCode() == null) return holding;
        return catalog.definition().assets().stream().filter(asset -> asset.code().equals(holding.stockCode())).findFirst()
                .map(asset -> new Holding(holding.stockCode(), asset.name(), asset.theme(), holding.shares(), holding.manualValue()))
                .orElse(holding);
    }

    /**
     * 推算交易后数值。组合尚未录入时返回 empty。计算完整且总资产创新高时上调已存的历史高点。
     */
    @Transactional
    public Optional<RiskBudget> riskBudget(String stockCode, double plannedBuy) {
        var asset = catalog.asset(stockCode);
        var entity = repository.findById(PortfolioEntity.SINGLETON_ID);
        if (entity.isEmpty()) return Optional.empty();
        var stored = entity.get().toProfile();
        var profile = withSnapshotPeak(stored);
        var budget = RiskBudgetCalculator.compute(profile, asset.code(), asset.theme(), plannedBuy,
                quotes(profile.holdings()), OffsetDateTime.now(MARKET_ZONE));
        // 只有这次计算出的总资产超过手填高点才回写；快照高点只参与计算，不改写用户填的数。
        if (budget.totalValue() != null && stored.peakValue() != null && budget.totalValue() > stored.peakValue()) {
            entity.get().setPeakValue(budget.totalValue());
            repository.save(entity.get());
        }
        return Optional.of(budget);
    }

    /** 按当期行情给整个组合估值（拟买入 0）。任一市场定价持仓缺当期行情时 totalValue 为 null。 */
    public RiskBudget valuation(PortfolioProfile profile) {
        return RiskBudgetCalculator.compute(profile, null, null, 0, quotes(profile.holdings()),
                OffsetDateTime.now(MARKET_ZONE));
    }

    /** 历史高点取手填值与每日快照最大值中较大的一个；两者都没有时保持原样。 */
    private PortfolioProfile withSnapshotPeak(PortfolioProfile profile) {
        Double snapshotPeak = snapshots.maxTotalValue();
        if (snapshotPeak == null || snapshotPeak <= 0
                || (profile.peakValue() != null && profile.peakValue() >= snapshotPeak)) {
            return profile;
        }
        return new PortfolioProfile(profile.holdings(), profile.cash(), profile.lowVolatility(), snapshotPeak,
                profile.updatedAt());
    }

    /** 持仓体检（只读）：组合尚未录入时返回 empty。 */
    public Optional<PortfolioHealth> health() {
        var profile = get().map(this::withSnapshotPeak);
        if (profile.isEmpty()) return Optional.empty();
        var assets = catalog.definition().assets();
        LocalDate today = LocalDate.now(MARKET_ZONE);
        Map<String, CompletableFuture<PortfolioHealthCalculator.HoldingQuote>> futures = new LinkedHashMap<>();
        Map<String, PortfolioHealthCalculator.ReviewStatus> reviews = new HashMap<>();
        for (Holding holding : profile.get().holdings()) {
            if (holding.pricedByMarket()) {
                futures.put(holding.stockCode(), CompletableFuture.supplyAsync(() -> healthQuote(holding, today), io.executor()));
            }
            if (holding.stockCode() != null && assets.stream().anyMatch(asset -> asset.code().equals(holding.stockCode()))) {
                reviewStatus(holding.stockCode()).ifPresent(status -> reviews.put(holding.stockCode(), status));
            }
        }
        Map<String, PortfolioHealthCalculator.HoldingQuote> quotes = new HashMap<>();
        futures.forEach((code, future) -> quotes.put(code, future.join()));
        return Optional.of(PortfolioHealthCalculator.evaluate(profile.get(), assets, quotes, reviews, today,
                OffsetDateTime.now(MARKET_ZONE)));
    }

    private Optional<PortfolioHealthCalculator.ReviewStatus> reviewStatus(String code) {
        try {
            return history.getLatestPersonalReview(code)
                    .filter(report -> report.getPersonalModel() != null && report.getPersonalModel().evidence() != null)
                    .map(report -> new PortfolioHealthCalculator.ReviewStatus(report.getPersonalModel().evidence().getValidUntil()));
        } catch (RuntimeException e) {
            // 读取失败按"没有有效复核"提示，宁可多提醒一次复核，也不当作证据有效。
            log.warn("读取最近复核失败: stockCode={}", code, e);
            return Optional.empty();
        }
    }

    private PortfolioHealthCalculator.HoldingQuote healthQuote(Holding holding, LocalDate today) {
        try {
            MarketDataResolution resolution = marketData.fetch(holding.stockCode(), holding.name());
            if (resolution.status() == MarketDataResolution.Status.STALE_CACHE) {
                return PortfolioHealthCalculator.HoldingQuote.failed("实时行情不可用，只有过期缓存");
            }
            Double rsi = PersonalTimingCalculator.calculate(MarketDataMapper.toDTO(resolution), today).dailyRsi14();
            return PortfolioHealthCalculator.HoldingQuote.of(resolution.snapshot().quote().current(), rsi);
        } catch (RuntimeException e) {
            log.warn("持仓体检取数失败: stockCode={}, reason={}", holding.stockCode(), e.getMessage());
            return PortfolioHealthCalculator.HoldingQuote.failed("行情取数失败");
        }
    }

    private Map<String, RiskBudgetCalculator.Quote> quotes(List<Holding> holdings) {
        Map<String, CompletableFuture<RiskBudgetCalculator.Quote>> futures = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            if (!holding.pricedByMarket()) continue;
            futures.put(holding.stockCode(), CompletableFuture.supplyAsync(() -> quote(holding), io.executor()));
        }
        Map<String, RiskBudgetCalculator.Quote> result = new HashMap<>();
        futures.forEach((code, future) -> result.put(code, future.join()));
        return result;
    }

    private RiskBudgetCalculator.Quote quote(Holding holding) {
        try {
            MarketDataResolution resolution = marketData.fetch(holding.stockCode(), holding.name());
            if (resolution.status() == MarketDataResolution.Status.STALE_CACHE) {
                return RiskBudgetCalculator.Quote.failed("实时行情不可用，只有过期缓存");
            }
            return RiskBudgetCalculator.Quote.of(resolution.snapshot().quote().current(), resolution.status().name());
        } catch (RuntimeException e) {
            log.warn("组合估值取数失败: stockCode={}, reason={}", holding.stockCode(), e.getMessage());
            return RiskBudgetCalculator.Quote.failed("行情取数失败");
        }
    }
}
