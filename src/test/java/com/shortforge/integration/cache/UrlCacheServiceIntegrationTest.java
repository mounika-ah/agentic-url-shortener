package com.shortforge.integration.cache;

import com.shortforge.cache.UrlCacheService;
import com.shortforge.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.cache.url-ttl=10s"
})
class UrlCacheServiceIntegrationTest
        extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "short-url:";

    @Autowired
    private UrlCacheService urlCacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldStoreAndRetrieveOriginalUrl() {
        String shortCode = "cache001";
        String originalUrl =
                "https://example.com/cache-integration-test";

        urlCacheService.put(
                shortCode,
                originalUrl,
                null
        );

        Optional<String> result =
                urlCacheService.getOriginalUrl(shortCode);

        assertThat(result)
                .isPresent()
                .contains(originalUrl);
    }

    @Test
    void shouldReturnEmptyWhenShortCodeIsNotCached() {
        Optional<String> result =
                urlCacheService.getOriginalUrl("missing1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldEvictCachedUrl() {
        String shortCode = "cache002";
        String originalUrl =
                "https://example.com/cache-eviction-test";

        urlCacheService.put(
                shortCode,
                originalUrl,
                null
        );

        assertThat(
                urlCacheService.getOriginalUrl(shortCode)
        ).contains(originalUrl);

        urlCacheService.evict(shortCode);

        assertThat(
                urlCacheService.getOriginalUrl(shortCode)
        ).isEmpty();
    }

    @Test
    void shouldOverwriteExistingCachedUrl() {
        String shortCode = "cache003";

        urlCacheService.put(
                shortCode,
                "https://example.com/old",
                null
        );

        urlCacheService.put(
                shortCode,
                "https://example.com/new",
                null
        );

        assertThat(
                urlCacheService.getOriginalUrl(shortCode)
        ).contains("https://example.com/new");
    }

    @Test
    void shouldApplyDefaultTtlWhenExpirationIsNull() {
        String shortCode = "cache004";

        urlCacheService.put(
                shortCode,
                "https://example.com/default-ttl",
                null
        );

        Long ttlMilliseconds = getTtlMilliseconds(shortCode);

        assertThat(ttlMilliseconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(10_000L);
    }

    @Test
    void shouldUseUrlExpirationWhenItIsShorterThanDefaultTtl() {
        String shortCode = "cache005";

        Instant expiresAt =
                Instant.now().plusSeconds(3);

        urlCacheService.put(
                shortCode,
                "https://example.com/short-expiration",
                expiresAt
        );

        Long ttlMilliseconds = getTtlMilliseconds(shortCode);

        assertThat(ttlMilliseconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(3_000L);
    }

    @Test
    void shouldCapCacheTtlAtConfiguredDefaultTtl() {
        String shortCode = "cache006";

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));

        urlCacheService.put(
                shortCode,
                "https://example.com/long-expiration",
                expiresAt
        );

        Long ttlMilliseconds = getTtlMilliseconds(shortCode);

        assertThat(ttlMilliseconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(10_000L);
    }

    @Test
    void shouldNotCacheAlreadyExpiredUrl() {
        String shortCode = "cache007";

        urlCacheService.put(
                shortCode,
                "https://example.com/expired",
                Instant.now().minusSeconds(1)
        );

        assertThat(
                urlCacheService.getOriginalUrl(shortCode)
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(buildKey(shortCode))
        ).isFalse();
    }

    @Test
    void shouldNotCacheUrlThatExpiresImmediately() {
        String shortCode = "cache008";

        urlCacheService.put(
                shortCode,
                "https://example.com/expires-now",
                Instant.now()
        );

        assertThat(
                urlCacheService.getOriginalUrl(shortCode)
        ).isEmpty();
    }

    @Test
    void shouldUseExpectedRedisKeyPrefix() {
        String shortCode = "cache009";
        String originalUrl =
                "https://example.com/key-prefix";

        urlCacheService.put(
                shortCode,
                originalUrl,
                null
        );

        String rawRedisValue =
                redisTemplate.opsForValue()
                        .get(buildKey(shortCode));

        assertThat(rawRedisValue)
                .isEqualTo(originalUrl);
    }

    private Long getTtlMilliseconds(String shortCode) {
        return redisTemplate.getExpire(
                buildKey(shortCode),
                TimeUnit.MILLISECONDS
        );
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
