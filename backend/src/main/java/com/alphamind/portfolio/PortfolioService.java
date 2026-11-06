package com.alphamind.portfolio;

import com.alphamind.config.ConcurrencyConfig;
import com.alphamind.investment.PersonalModelCatalog;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.ResilientMarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PortfolioService(PortfolioRepository repository, PersonalModelCatalog catalog,
                            ResilientMarketDataService marketData, ConcurrencyConfig.IoExecutor io) {
        this.repository = repository;
        this.catalog = catalog;
        this.marketData = marketData;
        this.io = io;
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
        var profile = entity.get().toProfile();
        var budget = RiskBudgetCalculator.compute(profile, asset.code(), asset.theme(), plannedBuy,
                quotes(profile.holdings()), OffsetDateTime.now(MARKET_ZONE));
        if (budget.totalValue() != null && budget.peakValue() != null
                && (profile.peakValue() == null || budget.peakValue() > profile.peakValue())) {
            entity.get().setPeakValue(budget.peakValue());
            repository.save(entity.get());
        }
        return Optional.of(budget);
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
