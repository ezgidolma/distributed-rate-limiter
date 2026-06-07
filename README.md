# Rate Limiter

A Redis-backed fixed-window rate limiting service. A Spring Boot servlet filter limits requests per IP address and returns `429 Too Many Requests` when the limit is exceeded.

## Features

- **Fixed-window** algorithm with an atomic counter (Redis + Lua script)
- **IP-based** limiting with `X-Forwarded-For` support
- **Default limit:** 10 requests per minute
- `X-RateLimit-Remaining` and `Retry-After` response headers
- `/actuator` endpoints are excluded from rate limiting

## Requirements

- Java 17+
- Redis (default: `localhost:6379`)

## Setup and Run

### 1. Start Redis

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`.

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

Response headers:

| Header | Description |
|--------|-------------|
| `X-RateLimit-Remaining` | Remaining requests in the current window |
| `Retry-After` | Seconds to wait before retrying |

## Configuration

Limit values are defined in `RateLimiterConfig`:

```java
return new FixedWindowRateLimiter(redis, 10, Duration.ofMinutes(1));
```

Redis connection settings can be added to `application.properties`:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Architecture

```
Request → RateLimitFilter → RateLimiter (interface)
                                ↓
                      FixedWindowRateLimiter
                                ↓
                      Redis (Lua script: fixed_window.lua)
```

The Lua script atomically increments the counter, sets the window TTL, and calculates remaining quota and retry delay.

## Project Structure

```
src/main/java/com/example/ratelimiter/
├── RatelimiterApplication.java   # Entry point
├── config/RateLimiterConfig.java # Bean definitions
├── controller/DemoController.java
├── filter/RateLimitFilter.java   # Servlet filter
└── limiter/
    ├── RateLimiter.java          # Interface
    ├── FixedWindowRateLimiter.java
    └── RateLimitDecision.java

src/main/resources/
├── application.properties
└── lua/fixed_window.lua          # Redis Lua script
```

## Test

```bash
./mvnw test
```
