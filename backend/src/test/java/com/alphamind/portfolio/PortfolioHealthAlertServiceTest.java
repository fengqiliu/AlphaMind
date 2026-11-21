package com.alphamind.portfolio;

import com.alphamind.portfolio.PortfolioHealth.Finding;
import com.alphamind.portfolio.PortfolioHealth.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class PortfolioHealthAlertServiceTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T15:40:00+08:00");
    static final Finding BANK_OVER = new Finding(Kind.POSITION_LIMIT, "600036", "招商银行", "招商银行 6% 超过 5%", 6.0, 5.0);
    static final Finding AI_THEME = new Finding(Kind.THEME_LIMIT, null, "AI/电子链", "主题 25% 超过 20%", 25.0, 20.0);
    static final Finding HOT = new Finding(Kind.OVERHEATED, "601138", "工业富联", "日 RSI14 75 过热", 75.0, 70.0);
    static final Finding EXPIRING = new Finding(Kind.REVIEW_EXPIRING, "600036", "招商银行", "还剩 3 天", 3.0, 7.0);

    private final PortfolioService portfolio = mock(PortfolioService.class);
    private final PortfolioHealthAlertRepository alerts = mock(PortfolioHealthAlertRepository.class);
    private final PortfolioHealthStateRepository states = mock(PortfolioHealthStateRepository.class);
    private final Map<String, PortfolioHealthStateEntity> stateStore = new LinkedHashMap<>();
    private final List<PortfolioHealthAlertEntity> saved = new ArrayList<>();
    private PortfolioHealthAlertService service;

    static PortfolioHealth complete(Finding... findings) {
        return new PortfolioHealth(100_000.0, List.of(findings), List.of(), List.of(), NOW);
    }

    static PortfolioHealth incomplete(List<String> codes, Finding... findings) {
        return new PortfolioHealth(null, List.of(findings), codes.stream().map(c -> c + "：行情取数失败").toList(), codes, NOW);
    }

    @BeforeEach
    void setUp() {
        when(states.findAll()).thenAnswer(inv -> List.copyOf(stateStore.values()));
        when(states.saveAll(anyIterable())).thenAnswer(inv -> {
            Iterable<PortfolioHealthStateEntity> items = inv.getArgument(0);
            items.forEach(s -> stateStore.put(s.getFindingKey(), s));
            return items;
        });
        doAnswer(inv -> {
            Iterable<String> keys = inv.getArgument(0);
            keys.forEach(stateStore::remove);
            return null;
        }).when(states).deleteAllById(anyIterable());
        when(alerts.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        service = new PortfolioHealthAlertService(portfolio, alerts, states,
                Clock.fixed(Instant.parse("2026-11-20T07:40:00Z"), PortfolioHealthAlertService.MARKET_ZONE));
    }

    private List<String> run(PortfolioHealth... rounds) {
        for (var health : rounds) {
            when(portfolio.health()).thenReturn(Optional.of(health));
            service.check();
        }
        return saved.stream().map(a -> a.getKind() + ":" + a.getName()).toList();
    }

    @Test
    void newFindingAlertsOnceWhilePersisting() {
        assertEquals(List.of("POSITION_LIMIT:招商银行", "THEME_LIMIT:AI/电子链"),
                run(complete(BANK_OVER, AI_THEME), complete(BANK_OVER, AI_THEME), complete(BANK_OVER, AI_THEME)));
        assertEquals(6.0, saved.get(0).getValue());
        assertEquals(5.0, saved.get(0).getLimitValue());
    }

    @Test
    void clearedFindingAlertsAgainWhenItReturns() {
        assertEquals(List.of("POSITION_LIMIT:招商银行", "POSITION_LIMIT:招商银行"),
                run(complete(BANK_OVER), complete(), complete(BANK_OVER)));
        assertTrue(stateStore.containsKey("POSITION_LIMIT:600036"));
    }

    @Test
    void sameStockDifferentKindsAreSeparateItems() {
        assertEquals(List.of("POSITION_LIMIT:招商银行", "REVIEW_EXPIRING:招商银行"),
                run(complete(BANK_OVER), complete(BANK_OVER, EXPIRING)));
    }

    @Test
    void skippedPortfolioChecksKeepTheirStateInsteadOfCountingAsRecovered() {
        // 第二轮有持仓缺行情：组合级检查未执行，超线状态必须保持，否则第三轮会重复提醒
        assertEquals(List.of("POSITION_LIMIT:招商银行"),
                run(complete(BANK_OVER), incomplete(List.of("601138")), complete(BANK_OVER)));
    }

    @Test
    void overheatedStateIsKeptOnlyForTheHoldingWithoutQuote() {
        var otherHot = new Finding(Kind.OVERHEATED, "002475", "立讯精密", "日 RSI14 72 过热", 72.0, 70.0);
        run(complete(HOT, otherHot));
        // 第二轮：工业富联缺行情（未知→保持），立讯有行情且不再过热（→清除）
        run(incomplete(List.of("601138")));
        assertEquals(Set.of("OVERHEATED:601138"), stateStore.keySet());
    }

    @Test
    void reviewFindingsClearNormallyEvenWhenQuotesAreMissing() {
        run(complete(EXPIRING), incomplete(List.of("600036")));
        assertTrue(stateStore.isEmpty(), "证据类检查不依赖行情，本轮没有即已恢复");
    }

    @Test
    void noPortfolioWritesNothing() {
        when(portfolio.health()).thenReturn(Optional.empty());
        assertEquals(PortfolioHealthAlertService.Outcome.NO_PORTFOLIO, service.check().outcome());
        verifyNoInteractions(alerts);
        verify(states, never()).findAll();
    }

    @Test
    void summaryCountsCreatedAndCleared() {
        run(complete(BANK_OVER, AI_THEME));
        when(portfolio.health()).thenReturn(Optional.of(complete(AI_THEME, HOT)));
        var summary = service.check();
        assertEquals(2, summary.findings());
        assertEquals(1, summary.created());
        assertEquals(1, summary.cleared());
    }

    @Test
    void acknowledgeKeepsTheFirstTime() {
        var alert = PortfolioHealthAlertEntity.builder().id(3L).kind("THEME_LIMIT").name("AI/电子链").message("m")
                .triggeredAt(NOW).build();
        when(alerts.findById(3L)).thenReturn(Optional.of(alert));
        var first = service.acknowledge(3).orElseThrow().getAcknowledgedAt();
        alert.acknowledge(first.plusHours(1));
        assertEquals(first, alert.getAcknowledgedAt());
        assertTrue(service.acknowledge(4).isEmpty());
    }
}
