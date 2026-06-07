package com.example.ratelimiter.config;

import com.example.ratelimiter.limiter.FixedWindowRateLimiter;
import com.example.ratelimiter.limiter.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redis) {
        return new FixedWindowRateLimiter(redis, 10, Duration.ofMinutes(1));
    }
}