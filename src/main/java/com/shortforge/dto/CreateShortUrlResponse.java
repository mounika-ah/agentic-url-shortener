package com.shortforge.dto;

import java.time.Instant;

public record CreateShortUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt
) {
}
