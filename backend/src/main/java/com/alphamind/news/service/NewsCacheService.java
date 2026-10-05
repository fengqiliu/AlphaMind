package com.alphamind.news.service;

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
 * 新闻缓存。Redis 是共享缓存，本地 Map 是 Redis 不可用时的进程内降级，结构与行情缓存一致。
 * 新闻变化比行情慢，fresh TTL 默认 10 分钟；Redis 键保留到 staleMaxAge，
 * 新闻源失败时仍可显式返回带过期标记的最后成功数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCacheService {

    private static final String NEWS_PREFIX = "alphamind:news:snapshot:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, NewsSnapshot> localNews = new ConcurrentHashMap<>();

    @Value("${alphamind.news.cache.fresh-ttl-seconds:600}")
    private long freshTtlSeconds;

    @Value("${alphamind.news.cache.stale-max-age-seconds:604800}")
    private long staleMaxAgeSeconds;

    public Optional<CacheHit> getFresh(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= freshTtlSeconds);
    }

    public Optional<CacheHit> getStale(String stockCode) {
        return get(stockCode).filter(hit -> hit.ageSeconds() <= staleMaxAgeSeconds);
    }

    public void put(NewsSnapshot snapshot) {
        localNews.put(snapshot.stockCode(), snapshot);
        try {
            redisTemplate.opsForValue().set(
                    NEWS_PREFIX + snapshot.stockCode(), objectMapper.writeValueAsString(snapshot),
                    Duration.ofSeconds(Math.max(1, staleMaxAgeSeconds)));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Redis 新闻缓存写入失败，保留本地缓存: key={}, reason={}",
                    NEWS_PREFIX + snapshot.stockCode(), e.getMessage());
        }
    }

    private Optional<CacheHit> get(String stockCode) {
        NewsSnapshot value = readRedis(stockCode).orElseGet(() -> localNews.get(stockCode));
        if (value == null || value.retrievedAt() == null) {
            return Optional.empty();
        }

        long ageSeconds = Math.max(0, Duration.between(value.retrievedAt(), Instant.now()).getSeconds());
        if (ageSeconds > staleMaxAgeSeconds) {
            localNews.remove(stockCode, value);
            return Optional.empty();
        }
        return Optional.of(new CacheHit(value, ageSeconds));
    }

    private Optional<NewsSnapshot> readRedis(String stockCode) {
        try {
            Object cached = redisTemplate.opsForValue().get(NEWS_PREFIX + stockCode);
            if (cached instanceof NewsSnapshot snapshot) {
                localNews.put(stockCode, snapshot);
                return Optional.of(snapshot);
            }
            if (cached instanceof String json && !json.isBlank()) {
                NewsSnapshot snapshot = objectMapper.readValue(json, NewsSnapshot.class);
                localNews.put(stockCode, snapshot);
                return Optional.of(snapshot);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Redis 新闻缓存读取失败，尝试本地缓存: key={}, reason={}",
                    NEWS_PREFIX + stockCode, e.getMessage());
        }
        return Optional.empty();
    }

    public record CacheHit(NewsSnapshot snapshot, long ageSeconds) {}
}
