# Agentic URL Shortener Architecture

## Overview

The Agentic URL Shortener is a production-oriented URL shortening service built using Java 21 and Spring Boot. The application demonstrates an event-driven architecture with caching, idempotent request handling, analytics, containerized deployment, and API documentation.

---

# High-Level Architecture

```mermaid
flowchart LR

    Client["Client / Browser"]

    API["Spring Boot Application"]

    Controller["REST Controllers"]

    Service["Business Services"]

    DB[(PostgreSQL)]

    Redis[(Redis Cache)]

    Kafka[(Redpanda / Kafka)]

    Client --> API
    API --> Controller
    Controller --> Service

    Service --> DB
    Service --> Redis
    Service --> Kafka
```

---

# URL Creation Flow

```mermaid
sequenceDiagram

    participant Client
    participant Controller
    participant Idempotency
    participant Service
    participant Database
    participant Kafka

    Client->>Controller: POST /api/v1/urls

    Controller->>Idempotency: Validate Idempotency-Key

    alt Existing Request
        Idempotency-->>Controller: Previous Response
    else New Request
        Idempotency->>Service: Create URL
        Service->>Database: Save URL
        Database-->>Service: Saved Entity
        Service->>Kafka: Publish URL_CREATED Event
        Service-->>Controller: Short URL
    end

    Controller-->>Client: HTTP 201 Created
```

---

# Redirect Flow

```mermaid
sequenceDiagram

    participant Browser
    participant Application
    participant Redis
    participant Database

    Browser->>Application: GET /{shortCode}

    Application->>Redis: Lookup

    alt Cache Hit
        Redis-->>Application: Original URL
    else Cache Miss
        Application->>Database: Find URL
        Database-->>Application: URL
        Application->>Redis: Cache URL
    end

    Application->>Database: Increment Click Count

    Application-->>Browser: HTTP 302 Redirect
```

---

# Idempotency Flow

```mermaid
flowchart TD

    Request --> CheckExisting

    CheckExisting -->|Completed| ReturnCachedResponse

    CheckExisting -->|Not Found| AcquireLock

    AcquireLock --> ExecuteBusinessLogic

    ExecuteBusinessLogic --> SaveResponse

    SaveResponse --> ReturnResponse

    ExecuteBusinessLogic --> ReleaseLock
```

---

# Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| ShortUrlController | REST APIs |
| RedirectController | HTTP Redirects |
| ShortUrlService | Business Logic |
| UrlCacheService | Redis Operations |
| IdempotentUrlCreationService | Duplicate Request Prevention |
| UrlEventPublisher | Kafka Event Publishing |
| Flyway | Database Versioning |

---

# Database

PostgreSQL is the system of record.

Primary table:

```
short_url
```

Stores

- ID
- Short Code
- Original URL
- Created Timestamp
- Expiration Timestamp
- Click Count
- Active Status

---

# Redis

Redis implements the Cache-Aside pattern.

Benefits

- Faster redirects
- Reduced database load
- Lower latency

---

# Kafka Events

Whenever a URL is created, the application publishes an event.

Example

```json
{
  "eventType":"URL_CREATED",
  "shortCode":"AbCd1234",
  "originalUrl":"https://google.com"
}
```

Potential consumers

- Analytics Service
- Audit Service
- Notification Service

---

# Technology Stack

| Layer | Technology |
|--------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL |
| Cache | Redis |
| Messaging | Redpanda / Kafka |
| Migration | Flyway |
| Documentation | OpenAPI |
| Containerization | Docker Compose |
| Testing | JUnit 5 + Mockito |

---

# Design Decisions

## Cache Aside

Redis stores frequently accessed URLs while PostgreSQL remains the source of truth.

## Idempotency

Duplicate client requests return the original response instead of creating duplicate URLs.

## Event Driven

Kafka events decouple analytics and future downstream services.

## Docker

All infrastructure can be started with

```bash
docker compose up -d
```

---

# Future Improvements

- Testcontainers Integration Tests
- GitHub Actions CI/CD
- Kubernetes Deployment
- Distributed Click Aggregation
- QR Code Generation
- Custom Aliases
- Authentication
- Rate Limiting
- Micrometer Metrics
- OpenTelemetry Tracing