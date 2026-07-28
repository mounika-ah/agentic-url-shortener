package com.shortforge.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record CreateShortUrlRequest(

        @NotBlank(message = "Original URL is required")
        @URL(message = "Original URL must be valid")
        @Pattern(
                regexp = "^https?://.+$",
                message = "URL must use HTTP or HTTPS"
        )
        String originalUrl,

        @Future(message = "Expiration time must be in the future")
        Instant expiresAt
) {
}