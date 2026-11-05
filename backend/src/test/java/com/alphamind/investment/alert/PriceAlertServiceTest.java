package com.alphamind.investment.alert;

import com.alphamind.investment.PersonalModelCatalog;
import com.alphamind.investment.PersonalModelDefinition;
import com.alphamind.investment.PriceZonesTest;
import com.alphamind.market.provider.MarketQuote;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.MarketDataSnapshot;
import com.alphamind.market.service.ResilientMarketDataService;
import com.alphamind.market.service.MarketDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PriceAlertServiceTest {
    private final ResilientMarketDataService market = mock(ResilientMarketDataService.class);
    private final PriceAlertRepository alerts = mock(PriceAlertRepository.class);
    private final PriceAlertStateRepository states = mock(PriceAlertStateRepository.class);
    private final Map<String, PriceAlertStateEntity> stateStore = new HashMap<>();
    private final List<PriceAlertEntity> saved = new ArrayList<>();
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        var catalog = mock(PersonalModelCatalog.class);
        when(catalog.definition()).thenReturn(new PersonalModelDefinition("m", "个人模型", "1.1", "2026-01-01", "f", "sha", "p",
                List.of(PriceZonesTest.ASSET)));
        when(states.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stateStore.get(inv.<String>getArgument(0))));
        when(states.save(any())).thenAnswer(inv -> {
            PriceAlertStateEntity s = inv.getArgument(0);
            stateStore.put(s.getStockCode(), s);
            return s;
        });
        when(alerts.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        service = new PriceAlertService(catalog, market, alerts, states,
                Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), PriceAlertService.MARKET_ZONE));
    }

    private void price(double current, MarketDataResolution.Status status) {
        var quote = new MarketQuote("600036", "招商银行", current, current, current, current, current, 100, 100 * current,
                LocalDateTime.of(2026, 11, 5, 10, 0));
        var snapshot = new MarketDataSnapshot("600036", "招商银行", quote, List.of(), "fixture", "fixture", Instant.now());
        when(market.fetch("600036", "招商银行")).thenReturn(new MarketDataResolution(snapshot, status, 0, null));
    }

    private List<String> run(double... prices) {
        for (double p : prices) { price(p, MarketDataResolution.Status.LIVE); service.checkAll(); }
        return saved.stream().map(a -> a.getFromZone() + ">" + a.getToZone()).toList();
    }

    @Test
    void firstObservationInsideTierAlerts() {
        assertEquals(List.of("UNKNOWN>TIER_1"), run(38));
        assertEquals("1.1", saved.get(0).getModelVersion());
    }

    @Test
    void wobblingAroundTheSameTierAlertsOnce() {
        assertEquals(List.of("ABOVE_FIRST>TIER_1"), run(40, 38, 36.5, 38.5, 36.8));
    }

    @Test
    void deeperTiersAndBreakingTheThirdBandEachAlert() {
        assertEquals(List.of("ABOVE_FIRST>TIER_1", "BETWEEN_BANDS>TIER_2", "TIER_2>TIER_3", "TIER_3>BELOW_THIRD"),
                run(40, 38, 36.5, 35, 32, 30));
    }

    @Test
    void returningAboveFirstBandResetsSoTheNextEntryAlertsAgain() {
        assertEquals(List.of("ABOVE_FIRST>TIER_1", "ABOVE_FIRST>TIER_1"), run(40, 38, 40, 38));
    }

    @Test
    void staleCacheAndFailuresNeverAlert() {
        price(38, MarketDataResolution.Status.STALE_CACHE);
        var stale = service.checkAll();
        assertEquals(List.of("600036"), stale.skippedStale());
        assertEquals(0, stale.checked());

        when(market.fetch("600036", "招商银行")).thenThrow(new MarketDataUnavailableException("down"));
        assertEquals(List.of("600036"), service.checkAll().failed());
        assertTrue(saved.isEmpty());
        assertTrue(stateStore.isEmpty(), "未取得当期价格时不更新档位状态");
    }

    @Test
    void acknowledgeKeepsTheFirstTime() {
        var alert = PriceAlertEntity.builder().id(7L).stockCode("600036").stockName("招商银行").fromZone("ABOVE_FIRST")
                .toZone("TIER_1").price(38).modelVersion("1.1").triggeredAt(OffsetDateTime.now()).build();
        when(alerts.findById(7L)).thenReturn(Optional.of(alert));
        var first = service.acknowledge(7).orElseThrow().getAcknowledgedAt();
        alert.acknowledge(first.plusHours(1));
        assertEquals(first, alert.getAcknowledgedAt());
        assertTrue(service.acknowledge(8).isEmpty());
    }
}
