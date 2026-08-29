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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情快照的两级缓存。Redis 是共享缓存，本地 Map 是 Redis 不可用时的进程内降级。
 * Redis 键保留到 staleMaxAge，因此上游失败时仍可显式返回带过期标记的最后成功快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataCacheService {

    private static final String KEY_PREFIX = "alphamind:market:snapshot:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, MarketDataSnapshot> localCache = new ConcurrentHashMap<>();

    @Value("${alphamind.market.cache.fresh-ttl-seconds:30}")
    private long freshTtlSeconds;

    @Value("${alphamind.market.cache.stale-max-age-seconds:86400}")
    private long staleMaxAgeSeconds;

    public Optional<CacheHit> getFresh(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= freshTtlSeconds);
    }

    public Optional<CacheHit> getStale(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= staleMaxAgeSeconds);
    }

    public void put(MarketDataSnapshot snapshot) {
        String key = key(snapshot.stockCode());
        localCache.put(snapshot.stockCode(), snapshot);
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(snapshot),
                    Duration.ofSeconds(Math.max(1, staleMaxAgeSeconds)));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Redis 行情缓存写入失败，保留本地缓存: stockCode={}, reason={}",
                    snapshot.stockCode(), e.getMessage());
        }
    }

    private Optional<CacheHit> get(String stockCode) {
        MarketDataSnapshot snapshot = readRedis(stockCode).orElseGet(() -> localCache.get(stockCode));
        if (snapshot == null || snapshot.retrievedAt() == null) return Optional.empty();

        long ageSeconds = Math.max(0, Duration.between(snapshot.retrievedAt(), Instant.now()).getSeconds());
        if (ageSeconds > staleMaxAgeSeconds) {
            localCache.remove(stockCode, snapshot);
            return Optional.empty();
        }
        return Optional.of(new CacheHit(snapshot, ageSeconds));
    }

    private Optional<MarketDataSnapshot> readRedis(String stockCode) {
        try {
            Object cached = redisTemplate.opsForValue().get(key(stockCode));
            if (cached instanceof MarketDataSnapshot snapshot) {
                localCache.put(stockCode, snapshot);
                return Optional.of(snapshot);
            }
            if (cached instanceof String json && !json.isBlank()) {
                MarketDataSnapshot snapshot = objectMapper.readValue(json, MarketDataSnapshot.class);
                localCache.put(stockCode, snapshot);
                return Optional.of(snapshot);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Redis 行情缓存读取失败，尝试本地缓存: stockCode={}, reason={}",
                    stockCode, e.getMessage());
        }
        return Optional.empty();
    }

    private String key(String stockCode) {
        return KEY_PREFIX + stockCode;
    }

    public record CacheHit(MarketDataSnapshot snapshot, long ageSeconds) {}
}
