## Current Progress

### ✅ Completed

- Spring Boot 4.1
- PostgreSQL
- Redis Integration
- Flyway
- URL Shortening APIs
- Redirect APIs
- Analytics APIs
- Global Exception Handling
- Cache-Aside Redis Strategy
- Redirect Performance Optimization

### 🚧 Next

- Idempotency
- Kafka Events
- Agentic Workflow
- Tests

## Implemented APIs

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/urls` | Create a short URL |
| GET | `/{shortCode}` | Redirect to original URL |
| GET | `/api/v1/urls/{shortCode}/analytics` | Retrieve analytics |

### Create Short URL

```http
POST /api/v1/urls
Content-Type: application/json

{
  "originalUrl": "https://www.google.com"
}
```

Response

```json
{
  "shortCode": "40Af6s9L",
  "shortUrl": "http://localhost:8080/40Af6s9L",
  "originalUrl": "https://www.google.com"
}
```
## Redis Cache Architecture

The application uses the Cache-Aside pattern for redirect optimization.

Flow:

1. Client requests `/abc123`
2. Check Redis
3. Cache Hit → Return original URL
4. Cache Miss → Query PostgreSQL
5. Store result in Redis with TTL
6. Return original URL

### Redis Keys

short-url:{shortCode}

Example:

short-url:40Af6s9L