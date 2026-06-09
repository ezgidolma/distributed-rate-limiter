package com.example.ratelimiter.limiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379)
        );
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        // her testten önce Redis'i temizle
        redisTemplate.getConnectionFactory()
                .getConnection().serverCommands().flushAll();
    }

    // ---- Fixed Window testleri ----

    @Test
    void fixedWindow_allowsRequestsUnderLimit() {
        RateLimiter limiter = new FixedWindowRateLimiter(
                redisTemplate, 5, Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = limiter.tryAcquire("test-ip");
            assertTrue(decision.allowed(), "İstek " + (i + 1) + " geçmeli");
        }
    }

    @Test
    void fixedWindow_blocksRequestsOverLimit() {
        RateLimiter limiter = new FixedWindowRateLimiter(
                redisTemplate, 5, Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("test-ip");
        }

        RateLimitDecision decision = limiter.tryAcquire("test-ip");
        assertFalse(decision.allowed(), "6. istek reddedilmeli");
        assertTrue(decision.retryAfterMillis() > 0, "retryAfter pozitif olmalı");
    }

    @Test
    void fixedWindow_differentKeysAreIndependent() {
        RateLimiter limiter = new FixedWindowRateLimiter(
                redisTemplate, 2, Duration.ofMinutes(1));

        limiter.tryAcquire("ip-A");
        limiter.tryAcquire("ip-A");

        RateLimitDecision decisionA = limiter.tryAcquire("ip-A");
        RateLimitDecision decisionB = limiter.tryAcquire("ip-B");

        assertFalse(decisionA.allowed(), "ip-A limiti aşmalı");
        assertTrue(decisionB.allowed(), "ip-B bağımsız olmalı");
    }

    // ---- Sliding Window testleri ----

    @Test
    void slidingWindow_allowsRequestsUnderLimit() {
        RateLimiter limiter = new SlidingWindowRateLimiter(
                redisTemplate, 5, Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = limiter.tryAcquire("test-ip");
            assertTrue(decision.allowed(), "İstek " + (i + 1) + " geçmeli");
        }
    }

    @Test
    void slidingWindow_blocksRequestsOverLimit() {
        RateLimiter limiter = new SlidingWindowRateLimiter(
                redisTemplate, 5, Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("test-ip");
        }

        RateLimitDecision decision = limiter.tryAcquire("test-ip");
        assertFalse(decision.allowed(), "6. istek reddedilmeli");
    }

    @Test
    void slidingWindow_differentKeysAreIndependent() {
        RateLimiter limiter = new SlidingWindowRateLimiter(
                redisTemplate, 2, Duration.ofMinutes(1));

        limiter.tryAcquire("ip-A");
        limiter.tryAcquire("ip-A");

        RateLimitDecision decisionA = limiter.tryAcquire("ip-A");
        RateLimitDecision decisionB = limiter.tryAcquire("ip-B");

        assertFalse(decisionA.allowed(), "ip-A limiti aşmalı");
        assertTrue(decisionB.allowed(), "ip-B bağımsız olmalı");
    }
}