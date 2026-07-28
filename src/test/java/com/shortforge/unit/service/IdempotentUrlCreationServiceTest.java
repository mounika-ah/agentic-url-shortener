package com.shortforge.unit.service;

import com.shortforge.cache.IdempotencyService;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.service.ShortUrlService;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.service.IdempotentUrlCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentUrlCreationServiceTest {

    private static final String IDEMPOTENCY_KEY =
            "create-url-request-001";

    @Mock
    private ShortUrlService shortUrlService;

    @Mock
    private IdempotencyService idempotencyService;

    private IdempotentUrlCreationService creationService;

    private CreateShortUrlRequest request;

    private CreateShortUrlResponse response;

    @BeforeEach
    void setUp() {
        creationService =
                new IdempotentUrlCreationService(
                        shortUrlService,
                        idempotencyService
                );

        request = new CreateShortUrlRequest(
                "https://www.google.com",
                null
        );

        response = new CreateShortUrlResponse(
                "AbCd1234",
                "http://localhost:8080/AbCd1234",
                "https://www.google.com",
                Instant.parse("2026-07-28T10:00:00Z"),
                null
        );
    }

    @Test
    void createShouldReturnPreviouslyCompletedResult() {
        when(idempotencyService.findCompletedResult(
                IDEMPOTENCY_KEY,
                request
        )).thenReturn(Optional.of(response));

        CreateShortUrlResponse result =
                creationService.create(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertThat(result)
                .isEqualTo(response);

        verify(idempotencyService, never())
                .acquireLock(anyString(), any());

        verify(shortUrlService, never())
                .create(any());
    }

    @Test
    void createShouldAcquireLockCreateAndStoreResult() {
        when(idempotencyService.findCompletedResult(
                IDEMPOTENCY_KEY,
                request
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(shortUrlService.create(request))
                .thenReturn(response);

        CreateShortUrlResponse result =
                creationService.create(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertThat(result)
                .isEqualTo(response);

        InOrder inOrder = inOrder(
                idempotencyService,
                shortUrlService
        );

        inOrder.verify(idempotencyService)
                .findCompletedResult(
                        IDEMPOTENCY_KEY,
                        request
                );

        inOrder.verify(idempotencyService)
                .acquireLock(
                        IDEMPOTENCY_KEY,
                        request
                );

        inOrder.verify(idempotencyService)
                .findCompletedResult(
                        IDEMPOTENCY_KEY,
                        request
                );

        inOrder.verify(shortUrlService)
                .create(request);

        inOrder.verify(idempotencyService)
                .storeCompletedResult(
                        IDEMPOTENCY_KEY,
                        request,
                        response
                );

        inOrder.verify(idempotencyService)
                .releaseLock(IDEMPOTENCY_KEY);
    }

    @Test
    void createShouldReturnResultCreatedByConcurrentRequest() {
        when(idempotencyService.findCompletedResult(
                IDEMPOTENCY_KEY,
                request
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(response));

        CreateShortUrlResponse result =
                creationService.create(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertThat(result)
                .isEqualTo(response);

        verify(shortUrlService, never())
                .create(any());

        verify(idempotencyService, never())
                .storeCompletedResult(
                        anyString(),
                        any(),
                        any()
                );

        verify(idempotencyService)
                .releaseLock(IDEMPOTENCY_KEY);
    }

    @Test
    void createShouldReleaseLockWhenUrlCreationFails() {
        when(idempotencyService.findCompletedResult(
                IDEMPOTENCY_KEY,
                request
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(shortUrlService.create(request))
                .thenThrow(
                        new IllegalStateException(
                                "Database unavailable"
                        )
                );

        assertThatThrownBy(() ->
                creationService.create(
                        IDEMPOTENCY_KEY,
                        request
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");

        verify(idempotencyService)
                .releaseLock(IDEMPOTENCY_KEY);

        verify(idempotencyService, never())
                .storeCompletedResult(
                        anyString(),
                        any(),
                        any()
                );
    }

    @Test
    void createShouldNotReleaseLockWhenLockAcquisitionFails() {
        when(idempotencyService.findCompletedResult(
                IDEMPOTENCY_KEY,
                request
        )).thenReturn(Optional.empty());

        doThrow(
                new IllegalStateException(
                        "Unable to acquire lock"
                )
        )
                .when(idempotencyService)
                .acquireLock(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertThatThrownBy(() ->
                creationService.create(
                        IDEMPOTENCY_KEY,
                        request
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to acquire lock");

        verify(idempotencyService, never())
                .releaseLock(anyString());

        verify(shortUrlService, never())
                .create(any());
    }
}
