package com.shortforge.cache;

import com.shortforge.dto.CreateShortUrlResponse;

public record IdempotencyResult(
        String requestFingerprint,
        CreateShortUrlResponse response
) {
}