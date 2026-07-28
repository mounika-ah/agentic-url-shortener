package com.shortforge.controller;

import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.service.IdempotentUrlCreationService;
import com.shortforge.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(
        name = "Short URL API",
        description = "Create shortened URLs and retrieve analytics"
)
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final IdempotentUrlCreationService creationService;

    public ShortUrlController(
            ShortUrlService shortUrlService,
            IdempotentUrlCreationService creationService
    ) {
        this.shortUrlService = shortUrlService;
        this.creationService = creationService;
    }

    @PostMapping
    @Operation(
            summary = "Create a short URL",
            description = """
                    Creates a shortened URL using an idempotency key.
                    Repeated requests with the same key and payload return
                    the previously created result.
                    """
    )
    public ResponseEntity<CreateShortUrlResponse> create(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            CreateShortUrlRequest request
    ) {
        CreateShortUrlResponse response =
                creationService.create(
                        idempotencyKey,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{shortCode}/analytics")
    @Operation(
            summary = "Get URL analytics",
            description = """
                    Returns URL metadata, click count,
                    expiration information and active status.
                    """
    )
    public UrlAnalyticsResponse getAnalytics(
            @PathVariable String shortCode
    ) {
        return shortUrlService.getAnalytics(shortCode);
    }
}