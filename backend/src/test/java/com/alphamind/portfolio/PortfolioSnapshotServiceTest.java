package com.alphamind.portfolio;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortfolioSnapshotServiceTest {
    private final PortfolioService portfolio = mock(PortfolioService.class);
    private final PortfolioSnapshotRepository repository = mock(PortfolioSnapshotRepository.class);
    private final PortfolioSnapshotService service = new PortfolioSnapshotService(portfolio, repository);

    private static final PortfolioProfile PROFILE = new PortfolioProfile(
            List.of(new Holding("600036", "招商银行", "银行", 1000.0, null)), 8_000.0, 2_000.0, null, null);

    private static RiskBudget valuation(Double total, List<String> problems) {
        return new RiskBudget(null, null, 0, problems.isEmpty(), total, null, null, null, null, null,
                List.of(), problems, OffsetDateTime.now());
    }

    private static PortfolioSnapshotEntity snapshot(String date, double total) {
        return new PortfolioSnapshotEntity(LocalDate.parse(date), total, 20, 1, OffsetDateTime.now());
    }

    @Test
    void recordsCompleteValuationWithCashRatioAndHoldingCount() {
        when(portfolio.get()).thenReturn(Optional.of(PROFILE));
        when(portfolio.valuation(PROFILE)).thenReturn(valuation(50_000.0, List.of()));

        var result = service.record();

        assertEquals(PortfolioSnapshotService.Outcome.RECORDED, result.outcome());
        var saved = ArgumentCaptor.forClass(PortfolioSnapshotEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(result.date(), saved.getValue().getSnapshotDate());
        assertEquals(50_000.0, saved.getValue().getTotalValue());
        assertEquals(20.0, saved.getValue().getCashRatio(), "现金 8000 + 低波动 2000 占 50000 的 20%");
        assertEquals(1, saved.getValue().getHoldingsCount());
    }

    @Test
    void missingQuoteLeavesTheDayBlankInsteadOfWritingAPartialTotal() {
        when(portfolio.get()).thenReturn(Optional.of(PROFILE));
        when(portfolio.valuation(PROFILE)).thenReturn(valuation(null, List.of("招商银行（600036）：行情取数失败")));

        var result = service.record();

        assertEquals(PortfolioSnapshotService.Outcome.INCOMPLETE, result.outcome());
        assertEquals(List.of("招商银行（600036）：行情取数失败"), result.problems());
        verify(repository, never()).save(any());
    }

    @Test
    void doesNothingBeforeAnyHoldingsAreEntered() {
        when(portfolio.get()).thenReturn(Optional.empty());

        assertEquals(PortfolioSnapshotService.Outcome.NO_PORTFOLIO, service.record().outcome());
        verify(repository, never()).save(any());
        verify(portfolio, never()).valuation(any());
    }

    @Test
    void drawdownUsesRunningPeakIncludingSnapshotsOutsideTheWindow() {
        var all = List.of(snapshot("2026-01-05", 100_000), snapshot("2026-06-01", 80_000), snapshot("2026-06-02", 90_000),
                snapshot("2026-06-03", 110_000), snapshot("2026-06-04", 99_000));

        var points = PortfolioSnapshotService.toPoints(all, LocalDate.parse("2026-06-01"));

        assertEquals(4, points.size(), "窗口之前的一条只参与滚动高点，不出现在结果里");
        assertEquals(20.0, points.get(0).drawdownPct());
        assertEquals(10.0, points.get(1).drawdownPct());
        assertEquals(0.0, points.get(2).drawdownPct(), "创新高当天回撤为 0");
        assertEquals(10.0, points.get(3).drawdownPct());
    }

    @Test
    void emptyHistoryGivesEmptyCurve() {
        assertTrue(PortfolioSnapshotService.toPoints(List.of(), LocalDate.parse("2026-01-01")).isEmpty());
    }
}
