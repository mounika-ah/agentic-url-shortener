package com.shortforge.integration.repository;

import com.shortforge.integration.AbstractIntegrationTest;
import com.shortforge.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortUrlRepositoryIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Test
    void shouldFindShortUrlByShortCode() {
        insertShortUrl(
                "repo1234",
                "https://example.com/repository-test",
                true,
                null,
                0L
        );

        var result =
                shortUrlRepository.findByShortCode("repo1234");

        assertThat(result).isPresent();

        Integer rowCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM short_urls
                WHERE short_code = ?
                  AND original_url = ?
                """,
                Integer.class,
                "repo1234",
                "https://example.com/repository-test"
        );

        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenShortCodeDoesNotExist() {
        var result =
                shortUrlRepository.findByShortCode("missing1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenShortCodeExists() {
        insertShortUrl(
                "exists01",
                "https://example.com/exists",
                true,
                null,
                0L
        );

        boolean exists =
                shortUrlRepository.existsByShortCode("exists01");

        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenShortCodeDoesNotExist() {
        boolean exists =
                shortUrlRepository.existsByShortCode("missing2");

        assertThat(exists).isFalse();
    }

    @Test
    void shouldEnforceUniqueShortCodeConstraint() {
        insertShortUrl(
                "unique01",
                "https://example.com/first",
                true,
                null,
                0L
        );

        assertThatThrownBy(() ->
                insertShortUrl(
                        "unique01",
                        "https://example.com/second",
                        true,
                        null,
                        0L
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void shouldIncrementClickCountForActiveNonExpiredUrl() {
        insertShortUrl(
                "click001",
                "https://example.com/active",
                true,
                OffsetDateTime.now().plusDays(1),
                0L
        );

        int updatedRows =
                shortUrlRepository.incrementClickCount("click001");

        assertThat(updatedRows).isEqualTo(1);

        Long clickCount = getClickCount("click001");

        assertThat(clickCount).isEqualTo(1L);
    }

    @Test
    @Transactional
    void shouldIncrementClickCountWhenExpirationIsNull() {
        insertShortUrl(
                "click002",
                "https://example.com/no-expiration",
                true,
                null,
                4L
        );

        int updatedRows =
                shortUrlRepository.incrementClickCount("click002");

        assertThat(updatedRows).isEqualTo(1);

        Long clickCount = getClickCount("click002");

        assertThat(clickCount).isEqualTo(5L);
    }

    @Test
    @Transactional
    void shouldNotIncrementClickCountForInactiveUrl() {
        insertShortUrl(
                "inactive",
                "https://example.com/inactive",
                false,
                null,
                3L
        );

        int updatedRows =
                shortUrlRepository.incrementClickCount("inactive");

        assertThat(updatedRows).isZero();

        Long clickCount = getClickCount("inactive");

        assertThat(clickCount).isEqualTo(3L);
    }

    @Test
    @Transactional
    void shouldNotIncrementClickCountForExpiredUrl() {
        insertShortUrl(
                "expired1",
                "https://example.com/expired",
                true,
                OffsetDateTime.now().minusMinutes(5),
                2L
        );

        int updatedRows =
                shortUrlRepository.incrementClickCount("expired1");

        assertThat(updatedRows).isZero();

        Long clickCount = getClickCount("expired1");

        assertThat(clickCount).isEqualTo(2L);
    }

    @Test
    @Transactional
    void shouldReturnZeroWhenIncrementingUnknownShortCode() {
        int updatedRows =
                shortUrlRepository.incrementClickCount("unknown1");

        assertThat(updatedRows).isZero();
    }

    @Test
    void shouldHaveFlywayManagedShortUrlsTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'short_urls'
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(1);
    }

    private void insertShortUrl(
            String shortCode,
            String originalUrl,
            boolean active,
            OffsetDateTime expiresAt,
            long clickCount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO short_urls (
                    short_code,
                    original_url,
                    active,
                    expires_at,
                    click_count,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                shortCode,
                originalUrl,
                active,
                expiresAt,
                clickCount,
                OffsetDateTime.now()
        );
    }

    private Long getClickCount(String shortCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT click_count
                FROM short_urls
                WHERE short_code = ?
                """,
                Long.class,
                shortCode
        );
    }
}