-- Initial database baseline.
-- Domain tables will be added in the next feature commit.

CREATE TABLE application_metadata
(
    id           BIGSERIAL PRIMARY KEY,
    application  VARCHAR(100)             NOT NULL,
    version      VARCHAR(50)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO application_metadata (application, version)
VALUES ('agentic-url-shortener', '0.1.0');