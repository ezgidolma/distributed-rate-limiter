package com.example.ratelimiter.config;

import com.example.ratelimiter.limiter.FixedWindowRateLimiter;
import com.example.ratelimiter.limiter.RateLimiter;
import com.example.ratelimiter.limiter.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Value("${ratelimit.algorithm:fixed-window}")
    private String algorithm;

    @Value("${ratelimit.limit:10}")
    private long limit;

    @Value("${ratelimit.window-millis:60000}")
    private long windowMillis;

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redis) {
        Duration window = Duration.ofMillis(windowMillis);

        return switch (algorithm) {
            case "sliding-window" -> new SlidingWindowRateLimiter(redis, limit, window);
            default -> new FixedWindowRateLimiter(redis, limit, window);
        };
    }
}