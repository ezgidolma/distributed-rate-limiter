package com.example.ratelimiter.filter;

import com.example.ratelimiter.limiter.RateLimitDecision;
import com.example.ratelimiter.limiter.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String key = getClientKey(request);

        Timer.Sample sample = Timer.start(meterRegistry);

        RateLimitDecision decision = rateLimiter.tryAcquire(key);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            Counter.builder("rate_limit_requests")
                    .tag("result", "allowed")
                    .tag("algorithm", rateLimiter.name())
                    .register(meterRegistry)
                    .increment();

            sample.stop(Timer.builder("rate_limit_duration")
                    .tag("result", "allowed")
                    .register(meterRegistry));

            chain.doFilter(request, response);
            return;
        }

        Counter.builder("rate_limit_requests")
                .tag("result", "denied")
                .tag("algorithm", rateLimiter.name())
                .register(meterRegistry)
                .increment();

        sample.stop(Timer.builder("rate_limit_duration")
                .tag("result", "denied")
                .register(meterRegistry));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After",
                String.valueOf((long) Math.ceil(decision.retryAfterMillis() / 1000.0)));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\",\"retryAfterMillis\":"
                        + decision.retryAfterMillis() + "}"
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private String getClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
