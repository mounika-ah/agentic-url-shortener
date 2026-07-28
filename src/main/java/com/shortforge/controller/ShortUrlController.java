package com.shortforge.controller;

import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.service.IdempotentUrlCreationService;
import com.shortforge.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
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
    public UrlAnalyticsResponse getAnalytics(
            @PathVariable String shortCode
    ) {
        return shortUrlService.getAnalytics(shortCode);
    }
}
