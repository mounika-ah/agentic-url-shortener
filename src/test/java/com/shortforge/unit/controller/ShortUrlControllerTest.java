package com.shortforge.unit.controller;

import com.shortforge.controller.ShortUrlController;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.service.IdempotentUrlCreationService;
import com.shortforge.service.ShortUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlControllerTest {

    private static final String IDEMPOTENCY_KEY =
            "create-url-request-001";

    private static final String SHORT_CODE =
            "AbCd1234";

    private static final String ORIGINAL_URL =
            "https://www.google.com";

    @Mock
    private ShortUrlService shortUrlService;

    @Mock
    private IdempotentUrlCreationService creationService;

    private ShortUrlController shortUrlController;

    @BeforeEach
    void setUp() {
        shortUrlController = new ShortUrlController(
                shortUrlService,
                creationService
        );
    }

    @Test
    void createShouldReturnCreatedResponse() {
        Instant createdAt =
                Instant.parse("2026-07-28T10:00:00Z");

        CreateShortUrlRequest request =
                new CreateShortUrlRequest(
                        ORIGINAL_URL,
                        null
                );

        CreateShortUrlResponse expectedResponse =
                new CreateShortUrlResponse(
                        SHORT_CODE,
                        "http://localhost:8080/" + SHORT_CODE,
                        ORIGINAL_URL,
                        createdAt,
                        null
                );

        when(creationService.create(
                IDEMPOTENCY_KEY,
                request
        )).thenReturn(expectedResponse);

        ResponseEntity<CreateShortUrlResponse> response =
                shortUrlController.create(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isEqualTo(expectedResponse);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().shortCode())
                .isEqualTo(SHORT_CODE);

        verify(creationService).create(
                IDEMPOTENCY_KEY,
                request
        );
    }

    @Test
    void getAnalyticsShouldReturnAnalyticsResponse() {
        Instant createdAt =
                Instant.parse("2026-07-28T10:00:00Z");

        UrlAnalyticsResponse expectedResponse =
                new UrlAnalyticsResponse(
                        SHORT_CODE,
                        ORIGINAL_URL,
                        5L,
                        createdAt,
                        null,
                        true
                );

        when(shortUrlService.getAnalytics(SHORT_CODE))
                .thenReturn(expectedResponse);

        UrlAnalyticsResponse response =
                shortUrlController.getAnalytics(
                        SHORT_CODE
                );

        assertThat(response)
                .isEqualTo(expectedResponse);

        assertThat(response.shortCode())
                .isEqualTo(SHORT_CODE);

        assertThat(response.clickCount())
                .isEqualTo(5L);

        assertThat(response.active())
                .isTrue();

        verify(shortUrlService)
                .getAnalytics(SHORT_CODE);
    }
}
