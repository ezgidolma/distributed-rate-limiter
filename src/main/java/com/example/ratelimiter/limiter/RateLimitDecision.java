package com.example.ratelimiter.limiter;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMillis) {

    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, Math.max(remaining, 0), 0);
    }

    public static RateLimitDecision denied(long retryAfterMillis) {
        return new RateLimitDecision(false, 0, Math.max(retryAfterMillis, 0));
    }
}