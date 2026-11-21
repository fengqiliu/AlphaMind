package com.alphamind.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 把每日体检的结果变成"新出现"的提醒：某一项首次越线时提醒一次，持续越线不重复，消失后清除。
 * 缺当期行情而无法检查的项保持原状态，避免行情一断就清除、恢复后又重复提醒。
 *
 * <p>先写提醒再改状态：中途失败时宁可下次重复提醒，也不漏掉一条。
 */
@Slf4j
@Service
public class PortfolioHealthAlertService {
    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    /** 依赖组合总资产的检查项；总资产不可信（有持仓缺行情）的那一轮它们都未执行。 */
    static final Set<PortfolioHealth.Kind> PORTFOLIO_LEVEL = EnumSet.of(PortfolioHealth.Kind.POSITION_LIMIT,
            PortfolioHealth.Kind.THEME_LIMIT, PortfolioHealth.Kind.CASH_FLOOR, PortfolioHealth.Kind.DRAWDOWN);

    public enum Outcome { CHECKED, NO_PORTFOLIO }

    public record Summary(Outcome outcome, int findings, int created, int cleared, List<String> unavailable) {}

    /** 本轮的状态转移：需要提醒的项、下一轮的越线集合（key → 种类）。 */
    record Plan(List<PortfolioHealth.Finding> alerts, Map<String, PortfolioHealth.Kind> nextActive) {}

    private final PortfolioService portfolio;
    private final PortfolioHealthAlertRepository alerts;
    private final PortfolioHealthStateRepository states;
    private final Clock clock;

    @Autowired
    public PortfolioHealthAlertService(PortfolioService portfolio, PortfolioHealthAlertRepository alerts,
                                       PortfolioHealthStateRepository states) {
        this(portfolio, alerts, states, Clock.system(MARKET_ZONE));
    }

    PortfolioHealthAlertService(PortfolioService portfolio, PortfolioHealthAlertRepository alerts,
                                PortfolioHealthStateRepository states, Clock clock) {
        this.portfolio = portfolio;
        this.alerts = alerts;
        this.states = states;
        this.clock = clock;
    }

    static String key(PortfolioHealth.Finding finding) {
        return finding.kind() + ":" + (finding.stockCode() != null ? finding.stockCode() : finding.name());
    }

    /** 某个已越线的项本轮是否"未知"（没有执行检查），未知时保持原状态。 */
    static boolean unknown(PortfolioHealth.Kind kind, String key, PortfolioHealth health) {
        if (PORTFOLIO_LEVEL.contains(kind)) return health.totalValue() == null;
        if (kind == PortfolioHealth.Kind.OVERHEATED) {
            String subject = key.substring(key.indexOf(':') + 1);
            return health.unavailableCodes().contains(subject);
        }
        return false;
    }

    static Plan plan(Map<String, PortfolioHealth.Kind> active, PortfolioHealth health) {
        Map<String, PortfolioHealth.Kind> next = new LinkedHashMap<>();
        List<PortfolioHealth.Finding> fresh = new ArrayList<>();
        for (var finding : health.findings()) {
            String key = key(finding);
            if (next.putIfAbsent(key, finding.kind()) == null && !active.containsKey(key)) fresh.add(finding);
        }
        active.forEach((key, kind) -> {
            if (!next.containsKey(key) && unknown(kind, key, health)) next.put(key, kind);
        });
        return new Plan(List.copyOf(fresh), next);
    }

    public Summary check() {
        var health = portfolio.health();
        if (health.isEmpty()) return new Summary(Outcome.NO_PORTFOLIO, 0, 0, 0, List.of());
        Map<String, PortfolioHealthStateEntity> stored = new LinkedHashMap<>();
        states.findAll().forEach(state -> stored.put(state.getFindingKey(), state));
        Map<String, PortfolioHealth.Kind> active = new LinkedHashMap<>();
        stored.forEach((key, state) -> active.put(key, PortfolioHealth.Kind.valueOf(state.getKind())));

        var plan = plan(active, health.get());
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (var finding : plan.alerts()) {
            alerts.save(PortfolioHealthAlertEntity.builder()
                    .kind(finding.kind().name()).stockCode(finding.stockCode()).name(finding.name())
                    .message(finding.message()).value(finding.value()).limitValue(finding.limit())
                    .triggeredAt(now).build());
            log.info("持仓体检提醒: {} {} {}", finding.kind(), finding.name(), finding.message());
        }
        List<String> cleared = stored.keySet().stream().filter(key -> !plan.nextActive().containsKey(key)).toList();
        if (!cleared.isEmpty()) states.deleteAllById(cleared);
        List<PortfolioHealthStateEntity> added = plan.nextActive().entrySet().stream()
                .filter(entry -> !stored.containsKey(entry.getKey()))
                .map(entry -> new PortfolioHealthStateEntity(entry.getKey(), entry.getValue().name(), now))
                .toList();
        if (!added.isEmpty()) states.saveAll(added);
        return new Summary(Outcome.CHECKED, health.get().findings().size(), plan.alerts().size(), cleared.size(),
                health.get().unavailable());
    }

    public List<PortfolioHealthAlertEntity> list(boolean includeAcknowledged) {
        return includeAcknowledged ? alerts.findTop50ByOrderByTriggeredAtDesc()
                : alerts.findTop50ByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
    }

    public long unreadCount() {
        return alerts.countByAcknowledgedAtIsNull();
    }

    /** 标记已读；不存在时返回 empty。重复标记保留第一次的时间。 */
    public Optional<PortfolioHealthAlertEntity> acknowledge(long id) {
        return alerts.findById(id).map(alert -> {
            alert.acknowledge(OffsetDateTime.now(clock));
            return alerts.save(alert);
        });
    }
}
