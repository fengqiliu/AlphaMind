package com.alphamind.investment.alert;

import com.alphamind.investment.PersonalModelCatalog;
import com.alphamind.investment.PersonalModelDefinition;
import com.alphamind.investment.PriceZones;
import com.alphamind.market.service.MarketDataResolution;
import com.alphamind.market.service.ResilientMarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 逐个检查个人模型观察标的的价格档位，价格首次进入更深的档位时写一条提醒。
 *
 * <p>只用实时行情或新鲜缓存；过期缓存不触发提醒，避免把旧价格当成当前价格。
 * 提醒只表示"该去复核了"，买入与否仍由复核时的全部决策门决定。
 */
@Slf4j
@Service
public class PriceAlertService {
    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final PersonalModelCatalog catalog;
    private final ResilientMarketDataService marketData;
    private final PriceAlertRepository alerts;
    private final PriceAlertStateRepository states;
    private final Clock clock;

    @Autowired
    public PriceAlertService(PersonalModelCatalog catalog, ResilientMarketDataService marketData,
                             PriceAlertRepository alerts, PriceAlertStateRepository states) {
        this(catalog, marketData, alerts, states, Clock.system(MARKET_ZONE));
    }

    PriceAlertService(PersonalModelCatalog catalog, ResilientMarketDataService marketData,
                      PriceAlertRepository alerts, PriceAlertStateRepository states, Clock clock) {
        this.catalog = catalog;
        this.marketData = marketData;
        this.alerts = alerts;
        this.states = states;
        this.clock = clock;
    }

    public record Summary(int checked, int created, List<String> skippedStale, List<String> failed) {}

    /** 档位转移的结果：是否提醒、新的最深已提醒档位。 */
    record Transition(boolean alert, int deepestAlerted) {}

    /**
     * 首档之上重置；进入比已提醒过的更深档位才提醒；档间和价格未知保持原状。
     * 这样价格在档边来回波动时只提醒一次。
     */
    static Transition transition(int deepestAlerted, PriceZones.Zone zone) {
        if (PriceZones.ABOVE_FIRST.equals(zone.name())) return new Transition(false, 0);
        int depth = PriceZones.depth(zone);
        return depth > deepestAlerted ? new Transition(true, depth) : new Transition(false, deepestAlerted);
    }

    public Summary checkAll() {
        var model = catalog.definition();
        int checked = 0, created = 0;
        List<String> stale = new ArrayList<>(), failed = new ArrayList<>();
        for (var asset : model.assets()) {
            MarketDataResolution resolution;
            try {
                resolution = marketData.fetch(asset.code(), asset.name());
            } catch (RuntimeException e) {
                log.warn("价格提醒取数失败: stockCode={}, reason={}", asset.code(), e.getMessage());
                failed.add(asset.code());
                continue;
            }
            if (resolution.status() == MarketDataResolution.Status.STALE_CACHE) {
                stale.add(asset.code());
                continue;
            }
            try {
                if (check(model, asset, resolution).isPresent()) created++;
                checked++;
            } catch (RuntimeException e) {
                log.warn("价格提醒写入失败: stockCode={}", asset.code(), e);
                failed.add(asset.code());
            }
        }
        return new Summary(checked, created, List.copyOf(stale), List.copyOf(failed));
    }

    private Optional<PriceAlertEntity> check(PersonalModelDefinition model, PersonalModelDefinition.Asset asset,
                                             MarketDataResolution resolution) {
        var quote = resolution.snapshot().quote();
        double price = quote.current();
        var zone = PriceZones.classify(asset, price);
        if (PriceZones.UNKNOWN.equals(zone.name())) return Optional.empty();
        OffsetDateTime now = OffsetDateTime.now(clock);
        var state = states.findById(asset.code()).orElse(null);
        String fromZone = state == null ? PriceZones.UNKNOWN : state.getLastZone();
        var next = transition(state == null ? 0 : state.getDeepestAlerted(), zone);

        Optional<PriceAlertEntity> alert = Optional.empty();
        if (next.alert()) {
            alert = Optional.of(alerts.save(PriceAlertEntity.builder()
                    .stockCode(asset.code()).stockName(asset.name())
                    .fromZone(fromZone).toZone(zone.name()).price(price)
                    .quotedAt(quote.quotedAt() == null ? null : quote.quotedAt().atZone(MARKET_ZONE).toOffsetDateTime())
                    .modelVersion(model.version()).triggeredAt(now)
                    .build()));
            log.info("价格提醒: {} {} {} → {}，价格 {}", asset.code(), asset.name(), fromZone, zone.name(), price);
        }
        states.save(new PriceAlertStateEntity(asset.code(), zone.name(), price, next.deepestAlerted(), now));
        return alert;
    }

    public List<PriceAlertEntity> list(boolean includeAcknowledged) {
        return includeAcknowledged ? alerts.findTop50ByOrderByTriggeredAtDesc()
                : alerts.findTop50ByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
    }

    public long unreadCount() {
        return alerts.countByAcknowledgedAtIsNull();
    }

    /** 标记已读；不存在时返回 empty。重复标记保留第一次的时间。 */
    public Optional<PriceAlertEntity> acknowledge(long id) {
        return alerts.findById(id).map(alert -> {
            alert.acknowledge(OffsetDateTime.now(clock));
            return alerts.save(alert);
        });
    }
}
