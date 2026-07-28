package com.shortforge.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.exception.IdempotencyConflictException;
import com.shortforge.exception.IdempotencyInProgressException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final String RESULT_PREFIX =
            "idempotency:result:";

    private static final String LOCK_PREFIX =
            "idempotency:lock:";

    private static final int MAX_KEY_LENGTH = 128;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration resultTtl;
    private final Duration lockTtl;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.result-ttl:24h}")
            Duration resultTtl,
            @Value("${app.idempotency.lock-ttl:60s}")
            Duration lockTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.resultTtl = resultTtl;
        this.lockTtl = lockTtl;
    }

    public Optional<CreateShortUrlResponse> findCompletedResult(
            String idempotencyKey,
            CreateShortUrlRequest request
    ) {
        validateKey(idempotencyKey);

        String storedValue = redisTemplate.opsForValue()
                .get(resultKey(idempotencyKey));

        if (storedValue == null) {
            return Optional.empty();
        }

        IdempotencyResult result = deserialize(storedValue);
        String fingerprint = fingerprint(request);

        if (!result.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used " +
                            "with a different request"
            );
        }

        return Optional.of(result.response());
    }

    public void acquireLock(
            String idempotencyKey,
            CreateShortUrlRequest request
    ) {
        validateKey(idempotencyKey);

        String fingerprint = fingerprint(request);

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(
                        lockKey(idempotencyKey),
                        fingerprint,
                        lockTtl
                );

        if (Boolean.TRUE.equals(acquired)) {
            return;
        }

        String existingFingerprint =
                redisTemplate.opsForValue()
                        .get(lockKey(idempotencyKey));

        if (existingFingerprint != null
                && !existingFingerprint.equals(fingerprint)) {

            throw new IdempotencyConflictException(
                    "Idempotency key is being used " +
                            "for a different request"
            );
        }

        throw new IdempotencyInProgressException(
                "A request with this idempotency key " +
                        "is already being processed"
        );
    }

    public void storeCompletedResult(
            String idempotencyKey,
            CreateShortUrlRequest request,
            CreateShortUrlResponse response
    ) {
        IdempotencyResult result = new IdempotencyResult(
                fingerprint(request),
                response
        );

        redisTemplate.opsForValue().set(
                resultKey(idempotencyKey),
                serialize(result),
                resultTtl
        );
    }

    public void releaseLock(String idempotencyKey) {
        redisTemplate.delete(lockKey(idempotencyKey));
    }

    private String fingerprint(CreateShortUrlRequest request) {
        String expiration = request.expiresAt() == null
                ? ""
                : request.expiresAt().toString();

        String canonicalRequest =
                request.originalUrl().trim() + "|" + expiration;

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private String serialize(IdempotencyResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize idempotency result",
                    exception
            );
        }
    }

    private IdempotencyResult deserialize(String value) {
        try {
            return objectMapper.readValue(
                    value,
                    IdempotencyResult.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to deserialize idempotency result",
                    exception
            );
        }
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed " +
                            MAX_KEY_LENGTH + " characters"
            );
        }
    }

    private String resultKey(String idempotencyKey) {
        return RESULT_PREFIX + idempotencyKey;
    }

    private String lockKey(String idempotencyKey) {
        return LOCK_PREFIX + idempotencyKey;
    }
}