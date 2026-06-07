package com.example.ratelimiter.limiter;

public interface RateLimiter {

    RateLimitDecision tryAcquire(String key);

    String name();
}