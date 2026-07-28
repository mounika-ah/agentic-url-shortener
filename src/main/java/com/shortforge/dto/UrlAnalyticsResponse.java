package com.shortforge.dto;

import java.time.Instant;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        long clickCount,
        Instant createdAt,
        Instant expiresAt,
        boolean active
) {
}
