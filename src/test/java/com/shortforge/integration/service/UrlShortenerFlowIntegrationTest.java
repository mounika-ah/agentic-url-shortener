package com.shortforge.integration.service;

import com.shortforge.event.UrlEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.shortforge.integration.AbstractIntegrationTest;
import com.shortforge.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UrlShortenerFlowIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UrlEventPublisher urlEventPublisher;

    @Test
    void shouldCreateShortUrlAndPersistIt() throws Exception {
        String originalUrl =
                "https://example.com/integration/create";

        String responseBody = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        "create-flow-key"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "%s",
                                          "expiresAt": null
                                        }
                                        """.formatted(originalUrl))
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.shortCode").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.shortUrl").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.originalUrl")
                                .value(originalUrl)
                )
                .andExpect(
                        jsonPath("$.createdAt").isNotEmpty()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson =
                jsonMapper.readTree(responseBody);

        String shortCode =
                responseJson.get("shortCode").asString();

        assertThat(
                shortUrlRepository.findByShortCode(shortCode)
        )
                .isPresent()
                .get()
                .satisfies(shortUrl -> {
                    assertThat(shortUrl.getShortCode())
                            .isEqualTo(shortCode);

                    assertThat(shortUrl.getOriginalUrl())
                            .isEqualTo(originalUrl);

                    assertThat(shortUrl.isActive())
                            .isTrue();

                    assertThat(shortUrl.getClickCount())
                            .isZero();
                });
    }

    @Test
    void shouldReturnSameResultForRepeatedIdempotentRequest()
            throws Exception {

        String idempotencyKey =
                "repeated-request-key";

        String requestBody = """
                {
                  "originalUrl":
                    "https://example.com/idempotent",
                  "expiresAt": null
                }
                """;

        String firstResponse = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstJson =
                jsonMapper.readTree(firstResponse);

        JsonNode secondJson =
                jsonMapper.readTree(secondResponse);

        String firstShortCode =
                firstJson.get("shortCode").asString();

        String secondShortCode =
                secondJson.get("shortCode").asString();

        assertThat(secondShortCode)
                .isEqualTo(firstShortCode);

        assertThat(secondJson.get("shortUrl").asString())
                .isEqualTo(
                        firstJson.get("shortUrl").asString()
                );

        assertThat(shortUrlRepository.count())
                .isEqualTo(1L);

        assertThat(
                redisTemplate.hasKey(
                        "idempotency:result:" +
                                idempotencyKey
                )
        ).isTrue();

        assertThat(
                redisTemplate.hasKey(
                        "idempotency:lock:" +
                                idempotencyKey
                )
        ).isFalse();
    }

    @Test
    void shouldRejectDifferentPayloadUsingSameIdempotencyKey()
            throws Exception {

        String idempotencyKey =
                "payload-conflict-key";

        mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "https://example.com/first",
                                          "expiresAt": null
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "https://example.com/second",
                                          "expiresAt": null
                                        }
                                        """)
                )
                .andExpect(status().isConflict());

        assertThat(shortUrlRepository.count())
                .isEqualTo(1L);
    }

    @Test
    void shouldCreateDifferentUrlsForDifferentIdempotencyKeys()
            throws Exception {

        String requestBody = """
                {
                  "originalUrl":
                    "https://example.com/different-keys",
                  "expiresAt": null
                }
                """;

        String firstResponse = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        "different-key-one"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        "different-key-two"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstShortCode =
                jsonMapper.readTree(firstResponse)
                        .get("shortCode")
                        .asString();

        String secondShortCode =
                jsonMapper.readTree(secondResponse)
                        .get("shortCode")
                        .asString();

        assertThat(secondShortCode)
                .isNotEqualTo(firstShortCode);

        assertThat(shortUrlRepository.count())
                .isEqualTo(2L);
    }

    @Test
    void shouldRedirectToOriginalUrl() throws Exception {
        String originalUrl =
                "https://example.com/redirect-target";

        String shortCode = createShortUrl(
                "redirect-flow-key",
                originalUrl,
                null
        );

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                "Location",
                                originalUrl
                        )
                );
    }

    @Test
    void shouldPopulateRedisCacheAfterRedirect()
            throws Exception {

        String originalUrl =
                "https://example.com/cache-population";

        String shortCode = createShortUrl(
                "cache-population-key",
                originalUrl,
                null
        );

        String redisKey =
                "short-url:" + shortCode;

        redisTemplate.delete(redisKey);

        assertThat(
                redisTemplate.hasKey(redisKey)
        ).isFalse();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().is3xxRedirection());

        assertThat(
                redisTemplate.opsForValue().get(redisKey)
        ).isEqualTo(originalUrl);
    }

    @Test
    void shouldIncrementClickCountAfterRedirect()
            throws Exception {

        String shortCode = createShortUrl(
                "click-count-key",
                "https://example.com/click-count",
                null
        );

        assertThat(getClickCount(shortCode))
                .isZero();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().is3xxRedirection());

        assertThat(getClickCount(shortCode))
                .isEqualTo(1L);

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().is3xxRedirection());

        assertThat(getClickCount(shortCode))
                .isEqualTo(2L);
    }

    @Test
    void shouldReturnAnalyticsForCreatedUrl()
            throws Exception {

        String originalUrl =
                "https://example.com/analytics-flow";

        String shortCode = createShortUrl(
                "analytics-flow-key",
                originalUrl,
                null
        );

        mockMvc.perform(
                        get(
                                "/api/v1/urls/{shortCode}/analytics",
                                shortCode
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.shortCode")
                                .value(shortCode)
                )
                .andExpect(
                        jsonPath("$.originalUrl")
                                .value(originalUrl)
                )
                .andExpect(
                        jsonPath("$.clickCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.active")
                                .value(true)
                );
    }

    @Test
    void shouldRejectRequestWithoutIdempotencyKey()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "https://example.com/missing-key",
                                          "expiresAt": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        assertThat(shortUrlRepository.count())
                .isZero();
    }

    @Test
    void shouldRejectInvalidOriginalUrl()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        "invalid-url-key"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "not-a-valid-url",
                                          "expiresAt": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        assertThat(shortUrlRepository.count())
                .isZero();
    }

    @Test
    void shouldRejectPastExpirationTime()
            throws Exception {

        String expiredTime =
                Instant.now()
                        .minus(Duration.ofHours(1))
                        .toString();

        mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        "past-expiration-key"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl":
                                            "https://example.com/past-expiration",
                                          "expiresAt":
                                            "%s"
                                        }
                                        """.formatted(expiredTime))
                )
                .andExpect(status().isBadRequest());

        assertThat(shortUrlRepository.count())
                .isZero();
    }

    @Test
    void shouldReturnNotFoundForUnknownShortCode()
            throws Exception {

        mockMvc.perform(get("/unknown-code"))
                .andExpect(status().isNotFound());
    }

    private String createShortUrl(
            String idempotencyKey,
            String originalUrl,
            Instant expiresAt
    ) throws Exception {

        String expirationJson = expiresAt == null
                ? "null"
                : "\"" + expiresAt + "\"";

        String responseBody = mockMvc.perform(
                        post("/api/v1/urls")
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "originalUrl": "%s",
                                          "expiresAt": %s
                                        }
                                        """.formatted(
                                        originalUrl,
                                        expirationJson
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return jsonMapper.readTree(responseBody)
                .get("shortCode")
                .asString();
    }

    private long getClickCount(String shortCode) {
        Long clickCount = jdbcTemplate.queryForObject(
                """
                select click_count
                  from short_urls
                 where short_code = ?
                """,
                Long.class,
                shortCode
        );

        return clickCount == null
                ? 0L
                : clickCount;
    }

}
