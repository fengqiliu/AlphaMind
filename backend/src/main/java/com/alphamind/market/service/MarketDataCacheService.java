package com.alphamind.market.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 行情缓存。Redis 是共享缓存，本地 Map 是 Redis 不可用时的进程内降级。
 * Redis 键保留到 staleMaxAge，因此上游失败时仍可显式返回带过期标记的最后成功数据。
 *
 * <p>缓存两类对象，各自独立命名空间：
 * <ul>
 *   <li>{@link MarketDataSnapshot}——报价 + 日 K 线，供完整分析流程使用</li>
 *   <li>{@link QuoteSnapshot}——仅报价，供股票搜索、自选股、周荐这类只要价格的场景使用，
 *       避免为了一个价格去拉 60 根 K 线</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataCacheService {

    private static final String SNAPSHOT_PREFIX = "alphamind:market:snapshot:";
    private static final String QUOTE_PREFIX = "alphamind:market:quote:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, MarketDataSnapshot> localSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuoteSnapshot> localQuotes = new ConcurrentHashMap<>();

    @Value("${alphamind.market.cache.fresh-ttl-seconds:30}")
    private long freshTtlSeconds;

    @Value("${alphamind.market.cache.stale-max-age-seconds:86400}")
    private long staleMaxAgeSeconds;

    // ── 完整快照（报价 + K 线）────────────────────────────────────────────────

    public Optional<CacheHit> getFresh(String stockCode) {
        return getSnapshot(stockCode).filter(hit -> hit.ageSeconds() <= freshTtlSeconds);
    }

    public Optional<CacheHit> getStale(String stockCode) {
        return getSnapshot(stockCode).filter(hit -> hit.ageSeconds() <= staleMaxAgeSeconds);
    }

    public void put(MarketDataSnapshot snapshot) {
        localSnapshots.put(snapshot.stockCode(), snapshot);
        write(SNAPSHOT_PREFIX, snapshot.stockCode(), snapshot);
    }

    private Optional<CacheHit> getSnapshot(String stockCode) {
        return read(SNAPSHOT_PREFIX, stockCode, MarketDataSnapshot.class, localSnapshots,
                MarketDataSnapshot::retrievedAt)
                .map(aged -> new CacheHit(aged.value(), aged.ageSeconds()));
    }

    // ── 仅报价 ───────────────────────────────────────────────────────────────

    public Optional<QuoteCacheHit> getFreshQuote(String stockCode) {
        return getQuote(stockCode).filter(hit -> hit.ageSeconds() <= freshTtlSeconds);
    }

    public Optional<QuoteCacheHit> getStaleQuote(String stockCode) {
        return getQuote(stockCode).filter(hit -> hit.ageSeconds() <= staleMaxAgeSeconds);
    }

    public void putQuote(QuoteSnapshot snapshot) {
        localQuotes.put(snapshot.stockCode(), snapshot);
        write(QUOTE_PREFIX, snapshot.stockCode(), snapshot);
    }

    private Optional<QuoteCacheHit> getQuote(String stockCode) {
        return read(QUOTE_PREFIX, stockCode, QuoteSnapshot.class, localQuotes, QuoteSnapshot::retrievedAt)
                .map(aged -> new QuoteCacheHit(aged.value(), aged.ageSeconds()));
    }

    // ── 共用的两级读写 ────────────────────────────────────────────────────────

    private <T> void write(String prefix, String stockCode, T value) {
        try {
            redisTemplate.opsForValue().set(
                    prefix + stockCode, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(Math.max(1, staleMaxAgeSeconds)));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Redis 行情缓存写入失败，保留本地缓存: key={}{}, reason={}",
                    prefix, stockCode, e.getMessage());
        }
    }

    private <T> Optional<Aged<T>> read(
            String prefix,
            String stockCode,
            Class<T> type,
            Map<String, T> localCache,
            Function<T, Instant> retrievedAt) {

        T value = readRedis(prefix, stockCode, type, localCache).orElseGet(() -> localCache.get(stockCode));
        if (value == null || retrievedAt.apply(value) == null) {
            return Optional.empty();
        }

        long ageSeconds = Math.max(0, Duration.between(retrievedAt.apply(value), Instant.now()).getSeconds());
        if (ageSeconds > staleMaxAgeSeconds) {
            localCache.remove(stockCode, value);
            return Optional.empty();
        }
        return Optional.of(new Aged<>(value, ageSeconds));
    }

    private <T> Optional<T> readRedis(String prefix, String stockCode, Class<T> type, Map<String, T> localCache) {
        try {
            Object cached = redisTemplate.opsForValue().get(prefix + stockCode);
            if (type.isInstance(cached)) {
                T value = type.cast(cached);
                localCache.put(stockCode, value);
                return Optional.of(value);
            }
            if (cached instanceof String json && !json.isBlank()) {
                T value = objectMapper.readValue(json, type);
                localCache.put(stockCode, value);
                return Optional.of(value);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Redis 行情缓存读取失败，尝试本地缓存: key={}{}, reason={}",
                    prefix, stockCode, e.getMessage());
        }
        return Optional.empty();
    }

    private record Aged<T>(T value, long ageSeconds) {}

    public record CacheHit(MarketDataSnapshot snapshot, long ageSeconds) {}

    public record QuoteCacheHit(QuoteSnapshot snapshot, long ageSeconds) {}
}
