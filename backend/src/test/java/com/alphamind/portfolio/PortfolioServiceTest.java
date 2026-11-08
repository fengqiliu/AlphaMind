package com.alphamind.portfolio;

import com.alphamind.config.ConcurrencyConfig;
import com.alphamind.investment.PersonalModelCatalog;
import com.alphamind.investment.PersonalModelDefinition;
import com.alphamind.market.provider.MarketQuote;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.MarketDataSnapshot;
import com.alphamind.market.service.ResilientMarketDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {
    private final PortfolioRepository repository = mock(PortfolioRepository.class);
    private final ResilientMarketDataService market = mock(ResilientMarketDataService.class);
    private final PortfolioSnapshotRepository snapshots = mock(PortfolioSnapshotRepository.class);
    private final ConcurrencyConfig.IoExecutor io = new ConcurrencyConfig.IoExecutor(Executors.newFixedThreadPool(2));
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        var catalog = mock(PersonalModelCatalog.class);
        var asset = new PersonalModelDefinition.Asset("600036", "招商银行", "银行", "银行",
                List.of(new PersonalModelDefinition.Band(1, 37, 39)), "", "", "", Map.of(), false);
        when(catalog.definition()).thenReturn(new PersonalModelDefinition("m", "n", "1.1", "d", "f", "s", "p", List.of(asset)));
        when(catalog.asset("600036")).thenReturn(asset);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PortfolioService(repository, catalog, market, io, mock(com.alphamind.service.AnalysisHistoryService.class), snapshots);
    }

    @AfterEach
    void tearDown() { io.close(); }

    private static PortfolioProfile input(Double peak, Holding... holdings) {
        return new PortfolioProfile(List.of(holdings), 10_000.0, 0.0, peak, null);
    }

    @Test
    void modelAssetsTakeNameAndThemeFromTheModel() {
        var saved = service.save(input(null, new Holding("600036", "招行", "随便写", 100.0, null)));
        assertEquals(new Holding("600036", "招商银行", "银行", 100.0, null), saved.holdings().get(0));
        assertNotNull(saved.updatedAt());
    }

    @Test
    void rejectsUnvaluableAndDuplicateHoldings() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(input(null, new Holding(null, "某基金", "债券", null, null))));
        assertThrows(IllegalArgumentException.class, () -> service.save(input(null,
                new Holding("600036", "a", "银行", 1.0, null), new Holding("600036", "b", "银行", 2.0, null))));
    }

    @Test
    void completeBudgetRaisesStoredPeakOnNewHigh() {
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID,
                List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000, 0, 15_000.0, null);
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.of(entity));
        var quote = new MarketQuote("600036", "招商银行", 38, 38, 38, 38, 38, 1, 38, LocalDateTime.now());
        when(market.fetch("600036", "招商银行")).thenReturn(new MarketDataResolution(
                new MarketDataSnapshot("600036", "招商银行", quote, List.of(), "f", "f", Instant.now()),
                MarketDataResolution.Status.LIVE, 0, null));

        var budget = service.riskBudget("600036", 2_000).orElseThrow();

        assertTrue(budget.complete(), budget.problems().toString());
        assertEquals(48_000.0, budget.totalValue());
        assertEquals(48_000.0, entity.getPeakValue());
        assertEquals(83.33, budget.projectedPositionPct());
    }

    @Test
    void staleQuoteIsReportedAndPeakUntouched() {
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID,
                List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000, 0, 15_000.0, null);
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.of(entity));
        var quote = new MarketQuote("600036", "招商银行", 38, 38, 38, 38, 38, 1, 38, LocalDateTime.now());
        when(market.fetch("600036", "招商银行")).thenReturn(new MarketDataResolution(
                new MarketDataSnapshot("600036", "招商银行", quote, List.of(), "f", "f", Instant.now()),
                MarketDataResolution.Status.STALE_CACHE, 900, "w"));

        var budget = service.riskBudget("600036", 0).orElseThrow();

        assertFalse(budget.complete());
        assertEquals(15_000.0, entity.getPeakValue());
        verify(repository, never()).save(any());
    }

    private void liveQuoteAt38() {
        var quote = new MarketQuote("600036", "招商银行", 38, 38, 38, 38, 38, 1, 38, LocalDateTime.now());
        when(market.fetch("600036", "招商银行")).thenReturn(new MarketDataResolution(
                new MarketDataSnapshot("600036", "招商银行", quote, List.of(), "f", "f", Instant.now()),
                MarketDataResolution.Status.LIVE, 0, null));
    }

    @Test
    void snapshotHighBeatsManualPeakForDrawdownWithoutRewritingIt() {
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID,
                List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000, 0, 15_000.0, null);
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.of(entity));
        when(snapshots.maxTotalValue()).thenReturn(60_000.0);
        liveQuoteAt38();

        var budget = service.riskBudget("600036", 0).orElseThrow();

        assertEquals(60_000.0, budget.peakValue());
        assertEquals(20.0, budget.portfolioDrawdownPct());
        assertEquals(48_000.0, entity.getPeakValue(), "只按本次总资产抬高手填高点，不写入快照高点");
    }

    @Test
    void snapshotHighIsUsedWhenNoManualPeakWasEntered() {
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID,
                List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000, 0, null, null);
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.of(entity));
        when(snapshots.maxTotalValue()).thenReturn(60_000.0);
        liveQuoteAt38();

        var budget = service.riskBudget("600036", 0).orElseThrow();

        assertEquals(20.0, budget.portfolioDrawdownPct());
        assertNull(entity.getPeakValue());
    }

    @Test
    void noSnapshotsLeavesManualPeakAsIs() {
        var entity = new PortfolioEntity(PortfolioEntity.SINGLETON_ID,
                List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 10_000, 0, 60_000.0, null);
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.of(entity));
        when(snapshots.maxTotalValue()).thenReturn(null);
        liveQuoteAt38();

        assertEquals(20.0, service.riskBudget("600036", 0).orElseThrow().portfolioDrawdownPct());
    }

    @Test
    void valuationIsWithheldWhenAHoldingHasOnlyStaleCache() {
        var quote = new MarketQuote("600036", "招商银行", 38, 38, 38, 38, 38, 1, 38, LocalDateTime.now());
        when(market.fetch("600036", "招商银行")).thenReturn(new MarketDataResolution(
                new MarketDataSnapshot("600036", "招商银行", quote, List.of(), "f", "f", Instant.now()),
                MarketDataResolution.Status.STALE_CACHE, 900, "w"));

        var valuation = service.valuation(input(null, new Holding("600036", "招商银行", "银行", 1000.0, null)));

        assertNull(valuation.totalValue());
        assertFalse(valuation.problems().isEmpty());
    }

    @Test
    void noPortfolioYetReturnsEmpty() {
        when(repository.findById(PortfolioEntity.SINGLETON_ID)).thenReturn(Optional.empty());
        assertTrue(service.riskBudget("600036", 0).isEmpty());
    }
}
