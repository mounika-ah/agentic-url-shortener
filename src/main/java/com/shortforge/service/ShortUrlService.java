package com.shortforge.service;

import com.shortforge.domain.ShortUrl;
import com.shortforge.dto.CreateShortUrlRequest;
import com.shortforge.dto.CreateShortUrlResponse;
import com.shortforge.dto.UrlAnalyticsResponse;
import com.shortforge.exception.ShortUrlNotFoundException;
import com.shortforge.exception.ShortUrlUnavailableException;
import com.shortforge.repository.ShortUrlRepository;
import com.shortforge.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shortforge.cache.UrlCacheService;

import java.time.Instant;

@Service
public class ShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final String baseUrl;
    private final UrlCacheService urlCacheService;

    public ShortUrlService(
            ShortUrlRepository repository,
            ShortCodeGenerator codeGenerator,
            UrlCacheService urlCacheService,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlCacheService = urlCacheService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        String shortCode = generateUniqueCode();
        Instant createdAt = Instant.now();

        ShortUrl shortUrl = new ShortUrl(
                shortCode,
                request.originalUrl(),
                createdAt,
                request.expiresAt()
        );

        ShortUrl saved = repository.save(shortUrl);

        return new CreateShortUrlResponse(
                saved.getShortCode(),
                baseUrl + "/" + saved.getShortCode(),
                saved.getOriginalUrl(),
                saved.getCreatedAt(),
                saved.getExpiresAt()
        );
    }

    @Transactional
    public String resolveAndRecordClick(String shortCode) {
        return urlCacheService.getOriginalUrl(shortCode)
                .map(originalUrl ->
                        handleCacheHit(shortCode, originalUrl)
                )
                .orElseGet(() ->
                        handleCacheMiss(shortCode)
                );
    }


    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(String shortCode) {
        ShortUrl shortUrl = find(shortCode);

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
                    "Short URL is no longer available: "
                            + shortCode
            );
        }

        return originalUrl;
    }

    private String handleCacheMiss(String shortCode) {
        ShortUrl shortUrl = find(shortCode);

        validateAvailability(shortUrl);

        shortUrl.recordClick();

        urlCacheService.put(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getExpiresAt()
        );

        return shortUrl.getOriginalUrl();
    }

    private void validateAvailability(ShortUrl shortUrl) {
        if (!shortUrl.isActive()) {
            throw new ShortUrlUnavailableException(
                    "Short URL is inactive: "
                            + shortUrl.getShortCode()
            );
        }

        if (shortUrl.isExpired(Instant.now())) {
            throw new ShortUrlUnavailableException(
                    "Short URL has expired: "
                            + shortUrl.getShortCode()
            );
        }
    }

    private ShortUrl find(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate();

            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique short code"
        );
    }
}
