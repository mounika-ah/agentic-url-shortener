package com.shortforge.service;

import com.shortforge.cache.UrlCacheService;
import com.shortforge.domain.ShortUrl;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.event.UrlEventPublisher;
import com.shortforge.exception.ShortUrlNotFoundException;
import com.shortforge.exception.ShortUrlUnavailableException;
import com.shortforge.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class ShortUrlService {

    private static final String CODE_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "abcdefghijklmnopqrstuvwxyz"
                    + "0123456789";

    private static final int SHORT_CODE_LENGTH = 8;

    private final ShortUrlRepository repository;
    private final UrlCacheService urlCacheService;
    private final UrlEventPublisher urlEventPublisher;
    private final SecureRandom secureRandom;
    private final String baseUrl;

    public ShortUrlService(
            ShortUrlRepository repository,
            UrlCacheService urlCacheService,
            UrlEventPublisher urlEventPublisher,
            @Value("${app.base-url:http://localhost:8080}")
            String baseUrl
    ) {
        this.repository = repository;
        this.urlCacheService = urlCacheService;
        this.urlEventPublisher = urlEventPublisher;
        this.baseUrl = baseUrl;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public CreateShortUrlResponse create(
            CreateShortUrlRequest request
    ) {
        String shortCode = generateUniqueCode();
        Instant createdAt = Instant.now();

        ShortUrl shortUrl = new ShortUrl(
                shortCode,
                request.originalUrl(),
                createdAt,
                request.expiresAt()
        );

        ShortUrl saved = repository.save(shortUrl);

        urlEventPublisher.publishCreated(
                saved.getShortCode(),
                saved.getOriginalUrl()
        );

        return new CreateShortUrlResponse(
                saved.getShortCode(),
                baseUrl + "/" + saved.getShortCode(),
                saved.getOriginalUrl(),
                saved.getCreatedAt(),
                saved.getExpiresAt()
        );
    }

    @Transactional
    public String resolveAndRecordClick(
            String shortCode
    ) {
        return urlCacheService.getOriginalUrl(shortCode)
                .map(originalUrl ->
                        handleCacheHit(
                                shortCode,
                                originalUrl
                        )
                )
                .orElseGet(() ->
                        handleCacheMiss(shortCode)
                );
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(
            String shortCode
    ) {
        ShortUrl shortUrl = repository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ShortUrlNotFoundException(
                                "Short URL not found: " + shortCode
                        )
                );

        return new UrlAnalyticsResponse(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt(),
                shortUrl.isActive()
        );
    }

    private String handleCacheHit(
            String shortCode,
            String originalUrl
    ) {
        int updatedRows =
                repository.incrementClickCount(shortCode);

        if (updatedRows == 0) {
            urlCacheService.evict(shortCode);

            throw new ShortUrlUnavailableException(
                    "Short URL is inactive or expired: "
                            + shortCode
            );
        }

        urlEventPublisher.publishVisited(
                shortCode,
                originalUrl
        );

        return originalUrl;
    }

    private String handleCacheMiss(
            String shortCode
    ) {
        ShortUrl shortUrl = repository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ShortUrlNotFoundException(
                                "Short URL not found: " + shortCode
                        )
                );

        validateAvailability(shortUrl);

        int updatedRows =
                repository.incrementClickCount(shortCode);

        if (updatedRows == 0) {
            throw new ShortUrlUnavailableException(
                    "Short URL is inactive or expired: "
                            + shortCode
            );
        }

        urlCacheService.put(
                shortCode,
                shortUrl.getOriginalUrl(),
                shortUrl.getExpiresAt()
        );

        urlEventPublisher.publishVisited(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl()
        );

        return shortUrl.getOriginalUrl();
    }

    private void validateAvailability(
            ShortUrl shortUrl
    ) {
        if (!shortUrl.isActive()) {
            urlCacheService.evict(
                    shortUrl.getShortCode()
            );

            throw new ShortUrlUnavailableException(
                    "Short URL is inactive: "
                            + shortUrl.getShortCode()
            );
        }

        if (shortUrl.isExpired(Instant.now())) {
            urlCacheService.evict(
                    shortUrl.getShortCode()
            );

            throw new ShortUrlUnavailableException(
                    "Short URL has expired: "
                            + shortUrl.getShortCode()
            );
        }
    }

    private String generateUniqueCode() {
        String shortCode;

        do {
            shortCode = generateCode();
        } while (repository.existsByShortCode(shortCode));

        return shortCode;
    }

    private String generateCode() {
        StringBuilder code =
                new StringBuilder(SHORT_CODE_LENGTH);

        for (int index = 0;
             index < SHORT_CODE_LENGTH;
             index++) {

            int characterIndex =
                    secureRandom.nextInt(
                            CODE_CHARACTERS.length()
                    );

            code.append(
                    CODE_CHARACTERS.charAt(
                            characterIndex
                    )
            );
        }

        return code.toString();
    }
}