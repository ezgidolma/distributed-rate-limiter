package com.example.ratelimiter.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class FixedWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final long limit;
    private final Duration window;
    private final RedisScript<List> script;

    public FixedWindowRateLimiter(StringRedisTemplate redis, long limit, Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
        this.script = new DefaultRedisScript<>(loadScript(), List.class);
    }

    @Override
    public RateLimitDecision tryAcquire(String key) {
        String redisKey = "ratelimit:fixed:" + key;

        List<Long> result = redis.execute(
                script,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(window.toMillis())
        );

        long allowed = result.get(0);
        long remaining = result.get(1);
        long retryAfter = result.get(2);

        if (allowed == 1) {
            return RateLimitDecision.allowed(remaining);
        }
        return RateLimitDecision.denied(retryAfter);
    }

    @Override
    public String name() {
        return "fixed-window";
    }

    private String loadScript() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("lua/fixed_window.lua").getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Lua script yüklenemedi", e);
        }
    }
}