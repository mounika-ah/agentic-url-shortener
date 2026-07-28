package com.shortforge.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class UrlCacheService {

    private static final String KEY_PREFIX = "short-url:";

    private final StringRedisTemplate redisTemplate;
    private final Duration defaultTtl;

    public UrlCacheService(
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.url-ttl:30m}") Duration defaultTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.defaultTtl = defaultTtl;
    }

    public Optional<String> getOriginalUrl(String shortCode) {
        String originalUrl = redisTemplate.opsForValue()
                .get(buildKey(shortCode));

        return Optional.ofNullable(originalUrl);
    }

    public void put(
            String shortCode,
            String originalUrl,
            Instant expiresAt
    ) {
        Duration ttl = calculateTtl(expiresAt);

        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        redisTemplate.opsForValue()
                .set(
                        buildKey(shortCode),
                        originalUrl,
                        ttl
                );
    }

    public void evict(String shortCode) {
        redisTemplate.delete(buildKey(shortCode));
    }

    private Duration calculateTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return defaultTtl;
        }

        Duration remaining =
                Duration.between(Instant.now(), expiresAt);

        return remaining.compareTo(defaultTtl) < 0
                ? remaining
                : defaultTtl;
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}