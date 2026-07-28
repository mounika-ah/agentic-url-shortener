# Agentic URL Shortener

A production-oriented URL Shortener built with **Java 21** and **Spring Boot 4.1**, demonstrating modern backend engineering practices including caching, idempotent request handling, event-driven architecture, API documentation, and containerized local development.

---

# Features

- URL Shortening
- HTTP Redirects (302)
- URL Analytics
- PostgreSQL Persistence
- Redis Cache-Aside Strategy
- Idempotent URL Creation
- Redis Processing Locks
- Kafka Event Publishing (Redpanda)
- Flyway Database Migrations
- OpenAPI / Swagger Documentation
- Docker Compose Local Environment
- Unit Test Coverage (JUnit 5 + Mockito)

---

# Architecture

Detailed architecture diagrams, request flows, and design decisions are available here:

📄 **[Architecture Documentation](docs/architecture.md)**

The architecture document includes:

- High-Level System Architecture
- URL Creation Flow
- Redirect Flow
- Redis Cache Strategy
- Kafka Event Flow
- Idempotency Design
- Database Design
- Design Decisions
- Future Enhancements

---

# Technology Stack

| Layer | Technology |
|--------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| Build Tool | Maven |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Messaging | Apache Kafka (Redpanda) |
| Database Migration | Flyway |
| API Documentation | OpenAPI / Swagger |
| Testing | JUnit 5, Mockito |
| Containerization | Docker Compose |

---

# Project Structure

```
agentic-url-shortener
│
├── src
│   ├── main
│   └── test
│
├── docs
│   └── architecture.md
│
├── Dockerfile
├── docker-compose.yml
├── README.md
└── pom.xml
```

---

# Running the Application

## Prerequisites

- Java 21
- Maven
- Docker Desktop

### Start Infrastructure

```bash
docker compose up -d
```

This starts:

- PostgreSQL
- Redis
- Redpanda (Kafka)
- Redpanda Console
- Spring Boot Application

---

## Run the Application

```bash
mvn spring-boot:run
```

Or run directly from IntelliJ IDEA.

---

# API Documentation

After the application starts:

Swagger UI

```
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON

```
http://localhost:8080/v3/api-docs
```

Health Endpoint

```
http://localhost:8080/actuator/health
```

Redpanda Console

```
http://localhost:8081
```

---

# REST APIs

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/urls` | Create Short URL |
| GET | `/{shortCode}` | Redirect to Original URL |
| GET | `/api/v1/urls/{shortCode}/analytics` | Retrieve URL Analytics |

---

# Example Request

## Create Short URL

```http
POST /api/v1/urls

Idempotency-Key: google-create-001

Content-Type: application/json

{
  "originalUrl":"https://www.google.com"
}
```

Response

```json
{
  "shortCode":"40Af6s9L",
  "shortUrl":"http://localhost:8080/40Af6s9L",
  "originalUrl":"https://www.google.com"
}
```

---

# Current Progress

## Completed

- Spring Boot 4.1
- PostgreSQL Persistence
- Redis Integration
- Flyway Migrations
- URL Shortening APIs
- Redirect APIs
- URL Analytics
- Global Exception Handling
- Redis Cache-Aside Strategy
- Idempotent Request Processing
- Redis Distributed Locks
- Kafka Event Publishing
- OpenAPI Documentation
- Docker Compose Environment
- Unit Testing

---

## In Progress

- GitHub Actions CI
- Integration Tests
- Performance Tests
- Metrics & Monitoring

---

# Git Commit Timeline

```
chore: bootstrap agentic URL shortener

feat: implement core URL shortening APIs

feat: optimize redirects with Redis cache-aside strategy

feat: implement idempotent URL creation

feat: publish URL events with Kafka

test: add service and controller test coverage

docs: add OpenAPI documentation

chore: add containerized local development stack

docs: add system architecture and design decisions
```

---

# Future Enhancements

- Testcontainers Integration Tests
- GitHub Actions CI/CD
- Kubernetes Deployment
- Rate Limiting
- URL Expiration Scheduler
- Custom URL Aliases
- QR Code Generation
- Authentication & Authorization
- Micrometer Metrics
- OpenTelemetry Tracing
- Distributed Click Aggregation

---

# Author

Built as an **Agentic Software Engineering** prototype demonstrating end-to-end SDLC automation, modern backend architecture, and production-ready engineering practices using Java and Spring Boot.