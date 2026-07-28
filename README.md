# Current Progress

## ✅ Completed

- Spring Boot 4.1 (Java 21)
- PostgreSQL Persistence
- Redis Integration
- Flyway Database Migrations
- URL Shortening APIs
- Redirect APIs
- URL Analytics APIs
- Global Exception Handling
- Redis Cache-Aside Strategy
- Redirect Performance Optimization
- Idempotent URL Creation
- Request Fingerprint Validation
- Redis Processing Locks
- Kafka Event Publishing

## 🚧 Upcoming

- Scheduled URL Expiration Cleanup
- Agentic Workflow Engine
- Integration & Performance Tests
- GitHub Actions CI/CD
- Micrometer Metrics & Monitoring

---

# Implemented APIs

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/urls` | Create a Short URL |
| GET | `/{shortCode}` | Redirect to Original URL |
| GET | `/api/v1/urls/{shortCode}/analytics` | Retrieve URL Analytics |

---

## Create Short URL

```http
POST /api/v1/urls
Content-Type: application/json
Idempotency-Key: google-create-001

{
  "originalUrl": "https://www.google.com"
}
```

### Response

```json
{
  "shortCode": "40Af6s9L",
  "shortUrl": "http://localhost:8080/40Af6s9L",
  "originalUrl": "https://www.google.com",
  "createdAt": "2026-07-28T05:30:00Z",
  "expiresAt": null
}
```

---

# Redis Cache Architecture

The application uses the **Cache-Aside** pattern to optimize redirect performance.

## Flow

1. Client requests `/{shortCode}`
2. Check Redis cache
3. Cache Hit → Return original URL immediately
4. Cache Miss → Load URL from PostgreSQL
5. Store URL in Redis with TTL
6. Return original URL to the client

### Redis Key Format

```
short-url:{shortCode}
```

Example:

```
short-url:40Af6s9L
```

---

# Idempotent URL Creation

The Create URL API supports the `Idempotency-Key` header to safely retry requests without creating duplicate short URLs.

## Behavior

- First request creates a new short URL.
- Repeating the same request with the same key returns the previously created response.
- Reusing the same key with a different request payload returns **409 Conflict**.
- Completed responses are cached in Redis for **24 hours**.
- A Redis processing lock prevents concurrent duplicate requests.

### Example

```http
POST /api/v1/urls
Idempotency-Key: google-create-001
Content-Type: application/json

{
  "originalUrl": "https://www.google.com"
}
```

---

# Kafka Event Streaming

The application publishes domain events asynchronously to Kafka, enabling event-driven integrations without impacting API response time.

## Topic

```
url-events
```

## Published Events

- `URL_CREATED`
- `URL_VISITED`

### Example Event

```json
{
  "type": "URL_CREATED",
  "shortCode": "40Af6s9L",
  "originalUrl": "https://www.google.com",
  "timestamp": "2026-07-28T05:30:00Z"
}
```

## Event Flow

```
Client Request
      │
      ▼
Spring Boot Service
      │
      ├── Persist to PostgreSQL
      ├── Cache in Redis (when applicable)
      └── Publish Kafka Event
               │
               ▼
         url-events Topic
```

Kafka events can be consumed by downstream services such as:

- Analytics
- Audit Logging
- Notification Services
- Recommendation Engines

---

# Technology Stack

- Java 21
- Spring Boot 4.1
- Spring Data JPA
- PostgreSQL 17
- Redis 7
- Apache Kafka (Redpanda)
- Flyway
- Maven
- Docker Compose

---

# Git Commit History

- ✅ `chore: bootstrap agentic URL shortener`
- ✅ `feat: implement core URL shortening APIs`
- ✅ `feat: optimize redirects with Redis cache-aside strategy`
- ✅ `feat: implement idempotent URL creation`
- ✅ `feat: publish URL events with Kafka`