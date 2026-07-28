CREATE TABLE short_urls
(
    id           BIGSERIAL PRIMARY KEY,
    short_code   VARCHAR(16) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE,
    click_count  BIGINT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_short_urls_expires_at
    ON short_urls (expires_at);