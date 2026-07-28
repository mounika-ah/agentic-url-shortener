package com.shortforge.service;

import com.shortforge.cache.IdempotencyService;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import org.springframework.stereotype.Service;

@Service
public class IdempotentUrlCreationService {

    private final ShortUrlService shortUrlService;
    private final IdempotencyService idempotencyService;

    public IdempotentUrlCreationService(
            ShortUrlService shortUrlService,
            IdempotencyService idempotencyService
    ) {
        this.shortUrlService = shortUrlService;
        this.idempotencyService = idempotencyService;
    }

    public CreateShortUrlResponse create(
            String idempotencyKey,
            CreateShortUrlRequest request
    ) {
        return idempotencyService
                .findCompletedResult(idempotencyKey, request)
                .orElseGet(() -> createNew(
                        idempotencyKey,
                        request
                ));
    }

    private CreateShortUrlResponse createNew(
            String idempotencyKey,
            CreateShortUrlRequest request
    ) {
        idempotencyService.acquireLock(
                idempotencyKey,
                request
        );

        try {
            var existingResult =
                    idempotencyService.findCompletedResult(
                            idempotencyKey,
                            request
                    );

            if (existingResult.isPresent()) {
                return existingResult.get();
            }

            CreateShortUrlResponse response =
                    shortUrlService.create(request);

            idempotencyService.storeCompletedResult(
                    idempotencyKey,
                    request,
                    response
            );

            return response;
        } finally {
            idempotencyService.releaseLock(idempotencyKey);
        }
    }
}