# Ledger

A personal finance API for tracking accounts, transactions, and budgets with built-in analytics and intelligent alerting. Designed as a production-ready Spring Boot service with JWT auth, PostgreSQL-backed data modeling, Redis caching, and scheduled financial insights.

## Stack

- **Spring Boot 3 (Java 21)** - REST API with layered architecture  
- **Spring Security + JWT** - Access tokens + refresh token rotation  
- **PostgreSQL + JPA** - Relational data modeling and persistence  
- **Flyway** - Versioned database migrations  
- **Redis** - Caching with TTL + write invalidation  
- **Spring Scheduler** - Background jobs for alerts and anomaly detection  
- **JUnit 5 + Testcontainers** - Unit + integration testing  
- **Docker + Docker Compose** - Local development and deployment 

## Endpoints

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register user |
| POST | `/api/auth/login` | Login, receive tokens |
| POST | `/api/auth/refresh` | Rotate refresh token |
| POST | `/api/auth/logout` | Revoke sessions |

### Accounts
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/accounts` | List accounts |
| POST | `/api/accounts` | Create account |
| PUT | `/api/accounts/{id}` | Update account |
| DELETE | `/api/accounts/{id}` | Delete account |

### Transactions
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/transactions` | Paginated transactions |
| POST | `/api/transactions` | Create transaction |
| PUT | `/api/transactions/{id}` | Update transaction |
| DELETE | `/api/transactions/{id}` | Delete transaction |

### Budgets
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/budgets` | Budgets with spend tracking |
| POST | `/api/budgets` | Create budget |
| PUT | `/api/budgets/{id}` | Update budget |
| DELETE | `/api/budgets/{id}` | Delete budget |

### Analytics
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/analytics/cashflow` | Income vs expenses |
| GET | `/api/analytics/networth` | Net worth |
| GET | `/api/analytics/burnrate` | Spend rate |
| GET | `/api/analytics/trends` | Monthly trends |
| GET | `/api/analytics/breakdown` | Category breakdown |

### Alerts
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/alerts` | Fetch alerts |
| PATCH | `/api/alerts/{id}/read` | Mark as read |
| PATCH | `/api/alerts/read-all` | Mark all as read |

## Features

- **Secure Auth** - JWT access tokens (15 min) + refresh token rotation (7 days) with revocation on logout
- **Multi-Account Management** - Checking, savings, credit, and investment accounts with real-time balance tracking
- **Transaction Engine** - Full CRUD with automatic balance adjustment on create/update/delete
- **Envelope Budgeting** - Per-category monthly budgets with real-time spend tracking
- **Recurring Payments** - Subscription tracking with automatic next-due-date advancement
- **Analytics API** - Cash flow, net worth, burn rate, monthly trends, and spending breakdown
- **Intelligent Alerting** - Daily scheduled jobs for budget breach detection (80%/100% thresholds) and statistical anomaly detection (z-score > 2σ)
- **Redis Caching** - Cache invalidation strategy on write operations with per-cache TTL configuration

## Getting Started

### Prerequisites
- Docker + Docker Compose
- Java 21
- Maven 3.9+

### Run with Docker Compose

```bash
# Clone the repo
git clone https://github.com/NicholasRader/Ledger.git
cd Ledger

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

## Running Tests

```bash
# Unit tests only
./mvnw test

# All tests including integration (requires Docker for Testcontainers)
./mvnw verify
```

## Project Structure

```
src/main/java/com/ledger/
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

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | `financeuser` | PostgreSQL username |
| `DB_PASSWORD` | `financepass` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | (see yml) | 256-bit hex secret for JWT signing |