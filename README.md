# Distributed Rate Limiter

A distributed API rate limiting service built with Spring Boot and Redis.
Multiple instances share the same limit state via Redis, so the limit holds
across a horizontally scaled deployment.

A Spring Boot servlet filter limits requests per IP address and returns
`429 Too Many Requests` when the limit is exceeded.

## Features

- **Fixed-window** and **Sliding-window** algorithms, switchable via config
- **Atomic Redis Lua scripts** — no race conditions under concurrent load
- **IP-based** limiting with `X-Forwarded-For` support
- **Distributed** — shared Redis counter works across multiple instances
- `X-RateLimit-Remaining` and `Retry-After` response headers
- `/actuator` endpoints are excluded from rate limiting
- Integration tested with Testcontainers (real Redis, no mocks)

## How it works

```
Request → RateLimitFilter → RateLimiter (interface)
                                ↓
                    ┌──────────────────────┐
                    │   Algorithm (config)  │
                    ├──────────────────────┤
                    │  FixedWindowLimiter   │
                    │  SlidingWindowLimiter │
                    └──────────────────────┘
                                ↓
                    Redis (atomic Lua script)
```

1. `RateLimitFilter` intercepts every request and extracts the client IP.
2. The active `RateLimiter` strategy runs an atomic Lua script in Redis.
3. Allowed requests reach the endpoint. Denied ones get `429 Too Many Requests`.

## Algorithms

| Algorithm      | How it works                        | Weakness                            | Status |
|----------------|-------------------------------------|-------------------------------------|--------|
| Fixed Window   | Counter per time bucket             | 2× burst at window boundary         | ✅ Done |
| Sliding Window | ZSET of timestamps, look back N ms  | More memory (one entry per request) | ✅ Done |

**Why Lua scripts?** Increment and expire must be atomic. Doing them as separate
Redis calls creates a race condition under concurrent load. Lua scripts execute
server-side as a single unit — no other command runs between them.

## Requirements

- Java 17+
- Docker

## Setup and Run

### 1. Start Redis

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`.

## Configuration

Set in `application.properties`:

```properties
ratelimit.algorithm=fixed-window   # fixed-window | sliding-window
ratelimit.limit=10                 # max requests per window
ratelimit.window-millis=60000      # window size in milliseconds

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Usage

### Demo endpoint

```bash
curl http://localhost:8080/api/hello
```

Successful response:

```json
{
  "message": "request allowed",
  "timestamp": "2026-06-07T13:52:45.123Z"
}
```

### Rate limit test

More than 10 requests from the same IP return `429`:

```bash
for i in $(seq 1 12); do
  echo -n "Request $i: "
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/hello
done
```

Response when the limit is exceeded:

```json
{
  "error": "rate_limit_exceeded",
  "retryAfterMillis": 45000
}
```

### Response headers

| Header                  | Description                              |
|-------------------------|------------------------------------------|
| `X-RateLimit-Remaining` | Remaining requests in the current window |
| `Retry-After`           | Seconds to wait before retrying          |

## Project Structure

```
src/main/java/com/example/ratelimiter/
├── RatelimiterApplication.java
├── config/
│   └── RateLimiterConfig.java        # Bean wiring, algorithm selection
├── controller/
│   └── DemoController.java           # Demo endpoint
├── filter/
│   └── RateLimitFilter.java          # Intercepts requests, returns 429
└── limiter/
    ├── RateLimiter.java              # Strategy interface
    ├── RateLimitDecision.java        # Decision record (allowed, remaining, retryAfter)
    ├── FixedWindowRateLimiter.java
    └── SlidingWindowRateLimiter.java

src/main/resources/
├── application.properties
└── lua/
    ├── fixed_window.lua
    └── sliding_window.lua

src/test/java/com/example/ratelimiter/limiter/
└── RateLimiterIntegrationTest.java   # 6 integration tests via Testcontainers
```

## Test

```bash
./mvnw test
```

Runs 6 integration tests against a real Redis container (Testcontainers).
No mocks — Lua scripts execute against actual Redis.

## Roadmap

- [x] Integration tests with Testcontainers (real Redis in tests)
- [ ] Prometheus metrics (allowed/denied counts, latency)
- [ ] Grafana dashboard
- [ ] Token Bucket algorithm (controlled burst)
- [ ] API key based limiting (instead of IP)
- [ ] Spring Cloud Gateway migration