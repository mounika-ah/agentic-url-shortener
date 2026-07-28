package com.shortforge.integration.service;

import com.shortforge.cache.IdempotencyService;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.exception.IdempotencyConflictException;
import com.shortforge.exception.IdempotencyInProgressException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = {
        "app.idempotency.result-ttl=10s",
        "app.idempotency.lock-ttl=5s"
})
class IdempotencyServiceIntegrationTest
        extends AbstractIntegrationTest {

    private static final String RESULT_PREFIX =
            "idempotency:result:";

    private static final String LOCK_PREFIX =
            "idempotency:lock:";

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldReturnEmptyWhenCompletedResultDoesNotExist() {
        CreateShortUrlRequest request =
                createRequest("https://example.com/missing");

        Optional<CreateShortUrlResponse> result =
                idempotencyService.findCompletedResult(
                        "missing-key",
                        request
                );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveCompletedResult() {
        String idempotencyKey = "completed-result-key";

        Instant createdAt = Instant.now();
        Instant expiresAt =
                Instant.now().plus(Duration.ofHours(1));

        CreateShortUrlRequest request =
                new CreateShortUrlRequest(
                        "https://example.com/article",
                        expiresAt
                );

        CreateShortUrlResponse response =
                new CreateShortUrlResponse(
                        "abc12345",
                        "http://localhost:8080/abc12345",
                        request.originalUrl(),
                        createdAt,
                        expiresAt
                );

        idempotencyService.storeCompletedResult(
                idempotencyKey,
                request,
                response
        );

        Optional<CreateShortUrlResponse> result =
                idempotencyService.findCompletedResult(
                        idempotencyKey,
                        request
                );

        assertThat(result).isPresent();

        assertThat(result.get().shortCode())
                .isEqualTo(response.shortCode());

        assertThat(result.get().shortUrl())
                .isEqualTo(response.shortUrl());

        assertThat(result.get().originalUrl())
                .isEqualTo(response.originalUrl());

        assertThat(result.get().createdAt())
                .isEqualTo(response.createdAt());

        assertThat(result.get().expiresAt())
                .isEqualTo(response.expiresAt());
    }

    @Test
    void shouldStoreCompletedResultWithConfiguredTtl() {
        String idempotencyKey = "result-ttl-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/result-ttl");

        CreateShortUrlResponse response =
                createResponse(
                        "ttl12345",
                        request
                );

        idempotencyService.storeCompletedResult(
                idempotencyKey,
                request,
                response
        );

        Long ttlMilliseconds = redisTemplate.getExpire(
                resultKey(idempotencyKey),
                TimeUnit.MILLISECONDS
        );

        assertThat(ttlMilliseconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(10_000L);
    }

    @Test
    void shouldAcquireLock() {
        String idempotencyKey = "acquire-lock-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/lock");

        assertThatCode(() ->
                idempotencyService.acquireLock(
                        idempotencyKey,
                        request
                )
        ).doesNotThrowAnyException();

        assertThat(
                redisTemplate.hasKey(lockKey(idempotencyKey))
        ).isTrue();
    }

    @Test
    void shouldApplyConfiguredLockTtl() {
        String idempotencyKey = "lock-ttl-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/lock-ttl");

        idempotencyService.acquireLock(
                idempotencyKey,
                request
        );

        Long ttlMilliseconds = redisTemplate.getExpire(
                lockKey(idempotencyKey),
                TimeUnit.MILLISECONDS
        );

        assertThat(ttlMilliseconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(5_000L);
    }

    @Test
    void shouldReleaseLock() {
        String idempotencyKey = "release-lock-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/release-lock");

        idempotencyService.acquireLock(
                idempotencyKey,
                request
        );

        assertThat(
                redisTemplate.hasKey(lockKey(idempotencyKey))
        ).isTrue();

        idempotencyService.releaseLock(idempotencyKey);

        assertThat(
                redisTemplate.hasKey(lockKey(idempotencyKey))
        ).isFalse();
    }

    @Test
    void shouldAllowLockToBeAcquiredAgainAfterRelease() {
        String idempotencyKey = "reacquire-lock-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/reacquire");

        idempotencyService.acquireLock(
                idempotencyKey,
                request
        );

        idempotencyService.releaseLock(idempotencyKey);

        assertThatCode(() ->
                idempotencyService.acquireLock(
                        idempotencyKey,
                        request
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSecondRequestWithSameKeyAndSamePayloadWhileLocked() {
        String idempotencyKey = "same-request-lock-key";

        CreateShortUrlRequest request =
                createRequest("https://example.com/in-progress");

        idempotencyService.acquireLock(
                idempotencyKey,
                request
        );

        assertThatThrownBy(() ->
                idempotencyService.acquireLock(
                        idempotencyKey,
                        request
                )
        )
                .isInstanceOf(
                        IdempotencyInProgressException.class
                )
                .hasMessageContaining(
                        "already being processed"
                );
    }

    @Test
    void shouldRejectDifferentRequestWhileKeyIsLocked() {
        String idempotencyKey = "different-request-lock-key";

        CreateShortUrlRequest firstRequest =
                createRequest("https://example.com/first");

        CreateShortUrlRequest secondRequest =
                createRequest("https://example.com/second");

        idempotencyService.acquireLock(
                idempotencyKey,
                firstRequest
        );

        assertThatThrownBy(() ->
                idempotencyService.acquireLock(
                        idempotencyKey,
                        secondRequest
                )
        )
                .isInstanceOf(
                        IdempotencyConflictException.class
                )
                .hasMessageContaining(
                        "different request"
                );
    }

    @Test
    void shouldRejectDifferentRequestForCompletedKey() {
        String idempotencyKey = "completed-conflict-key";

        CreateShortUrlRequest firstRequest =
                createRequest("https://example.com/original");

        CreateShortUrlRequest secondRequest =
                createRequest("https://example.com/different");

        CreateShortUrlResponse response =
                createResponse(
                        "conflict1",
                        firstRequest
                );

        idempotencyService.storeCompletedResult(
                idempotencyKey,
                firstRequest,
                response
        );

        assertThatThrownBy(() ->
                idempotencyService.findCompletedResult(
                        idempotencyKey,
                        secondRequest
                )
        )
                .isInstanceOf(
                        IdempotencyConflictException.class
                )
                .hasMessageContaining(
                        "different request"
                );
    }

    @Test
    void shouldTreatTrimmedOriginalUrlsAsSameRequest() {
        String idempotencyKey = "trimmed-url-key";

        CreateShortUrlRequest requestWithSpaces =
                new CreateShortUrlRequest(
                        "  https://example.com/trimmed  ",
                        null
                );

        CreateShortUrlRequest trimmedRequest =
                new CreateShortUrlRequest(
                        "https://example.com/trimmed",
                        null
                );

        CreateShortUrlResponse response =
                createResponse(
                        "trim12345",
                        requestWithSpaces
                );

        idempotencyService.storeCompletedResult(
                idempotencyKey,
                requestWithSpaces,
                response
        );

        Optional<CreateShortUrlResponse> result =
                idempotencyService.findCompletedResult(
                        idempotencyKey,
                        trimmedRequest
                );

        assertThat(result).isPresent();

        assertThat(result.get().shortCode())
                .isEqualTo("trim12345");
    }

    @Test
    void shouldTreatDifferentExpirationTimesAsDifferentRequests() {
        String idempotencyKey = "expiration-conflict-key";

        CreateShortUrlRequest firstRequest =
                new CreateShortUrlRequest(
                        "https://example.com/expiration",
                        Instant.parse("2030-01-01T10:00:00Z")
                );

        CreateShortUrlRequest secondRequest =
                new CreateShortUrlRequest(
                        "https://example.com/expiration",
                        Instant.parse("2030-01-01T11:00:00Z")
                );

        CreateShortUrlResponse response =
                createResponse(
                        "expiry001",
                        firstRequest
                );

        idempotencyService.storeCompletedResult(
                idempotencyKey,
                firstRequest,
                response
        );

        assertThatThrownBy(() ->
                idempotencyService.findCompletedResult(
                        idempotencyKey,
                        secondRequest
                )
        ).isInstanceOf(
                IdempotencyConflictException.class
        );
    }

    @Test
    void shouldRejectNullIdempotencyKey() {
        CreateShortUrlRequest request =
                createRequest("https://example.com/null-key");

        assertThatThrownBy(() ->
                idempotencyService.findCompletedResult(
                        null,
                        request
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Idempotency-Key header is required"
                );
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {
        CreateShortUrlRequest request =
                createRequest("https://example.com/blank-key");

        assertThatThrownBy(() ->
                idempotencyService.acquireLock(
                        "   ",
                        request
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Idempotency-Key header is required"
                );
    }

    @Test
    void shouldRejectIdempotencyKeyLongerThan128Characters() {
        String oversizedKey = "a".repeat(129);

        CreateShortUrlRequest request =
                createRequest("https://example.com/large-key");

        assertThatThrownBy(() ->
                idempotencyService.findCompletedResult(
                        oversizedKey,
                        request
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not exceed 128 characters"
                );
    }

    private CreateShortUrlRequest createRequest(
            String originalUrl
    ) {
        return new CreateShortUrlRequest(
                originalUrl,
                null
        );
    }

    private CreateShortUrlResponse createResponse(
            String shortCode,
            CreateShortUrlRequest request
    ) {
        return new CreateShortUrlResponse(
                shortCode,
                "http://localhost:8080/" + shortCode,
                request.originalUrl(),
                Instant.now(),
                request.expiresAt()
        );
    }

    private String resultKey(String idempotencyKey) {
        return RESULT_PREFIX + idempotencyKey;
    }

    private String lockKey(String idempotencyKey) {
        return LOCK_PREFIX + idempotencyKey;
    }
}
