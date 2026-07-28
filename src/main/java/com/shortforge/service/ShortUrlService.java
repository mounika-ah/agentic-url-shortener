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

import java.time.Instant;

@Service
public class ShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final String baseUrl;

    public ShortUrlService(
            ShortUrlRepository repository,
            ShortCodeGenerator codeGenerator,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
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
        ShortUrl shortUrl = find(shortCode);

        if (!shortUrl.isActive()) {
            throw new ShortUrlUnavailableException(
                    "Short URL is inactive: " + shortCode
            );
        }

        if (shortUrl.isExpired(Instant.now())) {
            throw new ShortUrlUnavailableException(
                    "Short URL has expired: " + shortCode
            );
        }

        shortUrl.recordClick();

        return shortUrl.getOriginalUrl();
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
