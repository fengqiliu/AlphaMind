package com.alphamind.fundamental.service;

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
 * 财报/估值缓存，结构与新闻缓存一致：Redis 共享缓存 + 本地 Map 降级。
 * 估值按交易日更新、财报按季度更新，fresh TTL 默认 6 小时；数据源失败时可返回 30 天内带过期标记的最后成功数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundamentalCacheService {

    private static final String PREFIX = "alphamind:fundamental:snapshot:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, FundamentalSnapshot> local = new ConcurrentHashMap<>();

    @Value("${alphamind.fundamental.cache.fresh-ttl-seconds:21600}")
    private long freshTtlSeconds;

    @Value("${alphamind.fundamental.cache.stale-max-age-seconds:2592000}")
    private long staleMaxAgeSeconds;

    public Optional<CacheHit> getFresh(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= freshTtlSeconds);
    }

    public Optional<CacheHit> getStale(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= staleMaxAgeSeconds);
    }

    public void put(FundamentalSnapshot snapshot) {
        local.put(snapshot.stockCode(), snapshot);
        try {
            redisTemplate.opsForValue().set(PREFIX + snapshot.stockCode(), objectMapper.writeValueAsString(snapshot),
                    Duration.ofSeconds(Math.max(1, staleMaxAgeSeconds)));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Redis 财报缓存写入失败，保留本地缓存: key={}, reason={}", PREFIX + snapshot.stockCode(), e.getMessage());
        }
    }

    private Optional<CacheHit> get(String stockCode) {
        FundamentalSnapshot value = readRedis(stockCode).orElseGet(() -> local.get(stockCode));
        if (value == null || value.retrievedAt() == null) return Optional.empty();
        long ageSeconds = Math.max(0, Duration.between(value.retrievedAt(), Instant.now()).getSeconds());
        if (ageSeconds > staleMaxAgeSeconds) {
            local.remove(stockCode, value);
            return Optional.empty();
        }
        return Optional.of(new CacheHit(value, ageSeconds));
    }

    private Optional<FundamentalSnapshot> readRedis(String stockCode) {
        try {
            Object cached = redisTemplate.opsForValue().get(PREFIX + stockCode);
            FundamentalSnapshot snapshot = cached instanceof FundamentalSnapshot direct ? direct
                    : cached instanceof String json && !json.isBlank() ? objectMapper.readValue(json, FundamentalSnapshot.class)
                    : null;
            if (snapshot != null) {
                local.put(stockCode, snapshot);
                return Optional.of(snapshot);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Redis 财报缓存读取失败，尝试本地缓存: key={}, reason={}", PREFIX + stockCode, e.getMessage());
        }
        return Optional.empty();
    }

    public record CacheHit(FundamentalSnapshot snapshot, long ageSeconds) {}
}
