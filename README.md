# Autonomous Financial Intelligence Platform

A production-grade REST API for personal finance management, built with Java 21 and Spring Boot 3. Features JWT authentication with refresh token rotation, a PostgreSQL-backed analytics engine, Redis caching, and an intelligent scheduling system for budget breach detection and anomaly alerts.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Auth | Spring Security + JJWT (JWT + Refresh Token Rotation) |
| Database | PostgreSQL 16 + Spring Data JPA |
| Migrations | Flyway |
| Caching | Redis + Spring Cache |
| Scheduling | Spring `@Scheduled` |
| Testing | JUnit 5, Mockito, Testcontainers |
| Docs | Springdoc OpenAPI / Swagger UI |
| DevOps | Docker + Docker Compose |

---

## Features

- **Secure Auth** — JWT access tokens (15 min) + refresh token rotation (7 days) with revocation on logout
- **Multi-Account Management** — Checking, savings, credit, and investment accounts with real-time balance tracking
- **Transaction Engine** — Full CRUD with automatic balance adjustment on create/update/delete
- **Envelope Budgeting** — Per-category monthly budgets with real-time spend tracking
- **Recurring Payments** — Subscription tracking with automatic next-due-date advancement
- **Analytics API** — Cash flow, net worth, burn rate, monthly trends, and spending breakdown
- **Intelligent Alerting** — Daily scheduled jobs for budget breach detection (80%/100% thresholds) and statistical anomaly detection (z-score > 2σ)
- **Redis Caching** — Cache invalidation strategy on write operations with per-cache TTL configuration

---

## Getting Started

### Prerequisites
- Docker + Docker Compose
- Java 21
- Maven 3.9+

### Run with Docker Compose

```bash
# Clone the repo
git clone https://github.com/yourusername/finance-tracker.git
cd finance-tracker

# Start PostgreSQL + Redis
docker-compose up postgres redis -d

# Run the application
./mvnw spring-boot:run
```

Or run the full stack (app + dependencies):
```bash
docker-compose up --build
```

### API Documentation

Once running, open Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

---

## API Overview

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, receive token pair |
| POST | `/api/auth/refresh` | Rotate refresh token |
| POST | `/api/auth/logout` | Revoke all refresh tokens |

### Accounts
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/accounts` | List all accounts |
| POST | `/api/accounts` | Create account |
| PUT | `/api/accounts/{id}` | Update account |
| DELETE | `/api/accounts/{id}` | Delete account |

### Transactions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/transactions?page=0&size=20` | Paginated transaction history |
| POST | `/api/transactions` | Record new transaction |
| PUT | `/api/transactions/{id}` | Update transaction |
| DELETE | `/api/transactions/{id}` | Delete transaction |

### Budgets
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/budgets?month=2025-03-01` | Budgets with real-time spend |
| POST | `/api/budgets` | Create budget |
| PUT | `/api/budgets/{id}` | Update limit |
| DELETE | `/api/budgets/{id}` | Delete budget |

### Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/analytics/cashflow?month=2025-03` | Income vs expenses |
| GET | `/api/analytics/networth` | Total net worth |
| GET | `/api/analytics/burnrate?categoryId=1&start=...&end=...` | Daily spend rate |
| GET | `/api/analytics/trends?months=6` | Month-over-month trends |
| GET | `/api/analytics/breakdown?start=...&end=...` | Spending by category |

### Alerts
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/alerts?unreadOnly=true` | Get alerts |
| PATCH | `/api/alerts/{id}/read` | Mark one as read |
| PATCH | `/api/alerts/read-all` | Mark all as read |

---

## Running Tests

```bash
# Unit tests only
./mvnw test

# All tests including integration (requires Docker for Testcontainers)
./mvnw verify
```

---

## Project Structure

```
src/main/java/com/financetracker/
├── config/          # SecurityConfig, CacheConfig, OpenApiConfig
├── controller/      # REST controllers
├── domain/
│   ├── entity/      # JPA entities
│   └── repository/  # Spring Data repositories
├── dto/             # Request/response records per domain
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── scheduler/       # FinanceScheduler (budget alerts, anomaly detection)
├── security/        # JwtService, JwtAuthenticationFilter
└── service/         # Business logic layer
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | `financeuser` | PostgreSQL username |
| `DB_PASSWORD` | `financepass` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | (see yml) | 256-bit hex secret for JWT signing |
