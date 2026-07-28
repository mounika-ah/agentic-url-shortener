package com.shortforge.unit.service;

import com.shortforge.cache.UrlCacheService;
import com.shortforge.service.ShortUrlService;
import com.shortforge.domain.ShortUrl;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.event.UrlEventPublisher;
import com.shortforge.exception.ShortUrlNotFoundException;
import com.shortforge.exception.ShortUrlUnavailableException;
import com.shortforge.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String ORIGINAL_URL =
            "https://www.google.com";

    private static final String SHORT_CODE =
            "AbCd1234";

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private UrlEventPublisher urlEventPublisher;

    private ShortUrlService shortUrlService;

    @BeforeEach
    void setUp() {
        shortUrlService = new ShortUrlService(
                repository,
                urlCacheService,
                urlEventPublisher,
                "http://localhost:8080"
        );
    }

    @Test
    void createShouldSaveShortUrlAndPublishCreatedEvent() {
        CreateShortUrlRequest request =
                new CreateShortUrlRequest(
                        ORIGINAL_URL,
                        null
                );

        when(repository.existsByShortCode(anyString()))
                .thenReturn(false);

        when(repository.save(any(ShortUrl.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        CreateShortUrlResponse response =
                shortUrlService.create(request);

        assertThat(response.shortCode())
                .isNotBlank();

        assertThat(response.shortCode())
                .hasSize(8);

        assertThat(response.shortUrl())
                .isEqualTo(
                        "http://localhost:8080/"
                                + response.shortCode()
                );

        assertThat(response.originalUrl())
                .isEqualTo(ORIGINAL_URL);

        assertThat(response.createdAt())
                .isNotNull();

        ArgumentCaptor<ShortUrl> captor =
                ArgumentCaptor.forClass(ShortUrl.class);

        verify(repository).save(captor.capture());

        ShortUrl savedUrl = captor.getValue();

        assertThat(savedUrl.getShortCode())
                .isEqualTo(response.shortCode());

        assertThat(savedUrl.getOriginalUrl())
                .isEqualTo(ORIGINAL_URL);

        verify(urlEventPublisher).publishCreated(
                response.shortCode(),
                ORIGINAL_URL
        );
    }

    @Test
    void resolveShouldReturnCachedUrlAndIncrementClickCount() {
        when(urlCacheService.getOriginalUrl(SHORT_CODE))
                .thenReturn(Optional.of(ORIGINAL_URL));

        when(repository.incrementClickCount(SHORT_CODE))
                .thenReturn(1);

        String result =
                shortUrlService.resolveAndRecordClick(
                        SHORT_CODE
                );

        assertThat(result)
                .isEqualTo(ORIGINAL_URL);

        verify(repository)
                .incrementClickCount(SHORT_CODE);

        verify(repository, never())
                .findByShortCode(anyString());

        verify(urlEventPublisher)
                .publishVisited(
                        SHORT_CODE,
                        ORIGINAL_URL
                );
    }

    @Test
    void resolveShouldLoadDatabaseWhenCacheMisses() {
        Instant expiresAt = Instant.now()
                .plus(1, ChronoUnit.DAYS);

        ShortUrl shortUrl = new ShortUrl(
                SHORT_CODE,
                ORIGINAL_URL,
                Instant.now(),
                expiresAt
        );

        when(urlCacheService.getOriginalUrl(SHORT_CODE))
                .thenReturn(Optional.empty());

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(shortUrl));

        when(repository.incrementClickCount(SHORT_CODE))
                .thenReturn(1);

        String result =
                shortUrlService.resolveAndRecordClick(
                        SHORT_CODE
                );

        assertThat(result)
                .isEqualTo(ORIGINAL_URL);

        verify(urlCacheService).put(
                SHORT_CODE,
                ORIGINAL_URL,
                expiresAt
        );

        verify(urlEventPublisher).publishVisited(
                SHORT_CODE,
                ORIGINAL_URL
        );
    }

    @Test
    void resolveShouldRejectExpiredUrl() {
        ShortUrl expiredUrl = new ShortUrl(
                SHORT_CODE,
                ORIGINAL_URL,
                Instant.now()
                        .minus(1, ChronoUnit.DAYS),
                Instant.now()
                        .minus(1, ChronoUnit.MINUTES)
        );

        when(urlCacheService.getOriginalUrl(SHORT_CODE))
                .thenReturn(Optional.empty());

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(expiredUrl));

        assertThatThrownBy(() ->
                shortUrlService.resolveAndRecordClick(
                        SHORT_CODE
                )
        )
                .isInstanceOf(
                        ShortUrlUnavailableException.class
                )
                .hasMessageContaining(SHORT_CODE);

        verify(urlCacheService)
                .evict(SHORT_CODE);

        verify(repository, never())
                .incrementClickCount(anyString());
    }

    @Test
    void resolveShouldRejectStaleCacheEntry() {
        when(urlCacheService.getOriginalUrl(SHORT_CODE))
                .thenReturn(Optional.of(ORIGINAL_URL));

        when(repository.incrementClickCount(SHORT_CODE))
                .thenReturn(0);

        assertThatThrownBy(() ->
                shortUrlService.resolveAndRecordClick(
                        SHORT_CODE
                )
        )
                .isInstanceOf(
                        ShortUrlUnavailableException.class
                )
                .hasMessageContaining(SHORT_CODE);

        verify(urlCacheService)
                .evict(SHORT_CODE);

        verify(urlEventPublisher, never())
                .publishVisited(anyString(), anyString());
    }

    @Test
    void getAnalyticsShouldReturnAnalytics() {
        Instant createdAt =
                Instant.parse("2026-07-28T10:00:00Z");

        ShortUrl shortUrl = new ShortUrl(
                SHORT_CODE,
                ORIGINAL_URL,
                createdAt,
                null
        );

        ReflectionTestUtils.setField(
                shortUrl,
                "clickCount",
                5L
        );

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(shortUrl));

        UrlAnalyticsResponse response =
                shortUrlService.getAnalytics(
                        SHORT_CODE
                );

        assertThat(response.shortCode())
                .isEqualTo(SHORT_CODE);

        assertThat(response.originalUrl())
                .isEqualTo(ORIGINAL_URL);

        assertThat(response.clickCount())
                .isEqualTo(5);

        assertThat(response.active())
                .isTrue();
    }

    @Test
    void getAnalyticsShouldThrowNotFound() {
        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                shortUrlService.getAnalytics(
                        SHORT_CODE
                )
        )
                .isInstanceOf(
                        ShortUrlNotFoundException.class
                );
    }
}