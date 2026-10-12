package com.example.demo.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import com.example.demo.error.GatewayErrors;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

public class GatewayRateLimitFilter extends OncePerRequestFilter {
    private final Cache<String, Bucket> buckets;
    private final GatewayErrors errors;
    private final MeterRegistry metrics;
    private final long capacity;
    private final long registrationCapacity;
    private final Duration period;

    public GatewayRateLimitFilter(GatewayErrors errors, MeterRegistry metrics, long capacity,
                                  long registrationCapacity, Duration period, long maximumKeys) {
        Assert.isTrue(capacity > 0 && registrationCapacity > 0 && maximumKeys > 0,
                "Rate limit capacities and maximum keys must be positive");
        Assert.isTrue(!period.isNegative() && !period.isZero(), "Rate limit period must be positive");
        this.errors = errors;
        this.metrics = metrics;
        this.capacity = capacity;
        this.registrationCapacity = registrationCapacity;
        this.period = period;
        this.buckets = Caffeine.newBuilder().maximumSize(maximumKeys)
                .expireAfterAccess(period.multipliedBy(2)).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String client = authentication != null && authentication.isAuthenticated()
                ? "user:" + authentication.getName() : "ip:" + request.getRemoteAddr();
        boolean registration = request.getMethod().equals("POST") && request.getRequestURI().equals("/api/customers");
        long limit = registration ? registrationCapacity : capacity;
        String group = registration ? "registration" : "api";
        Bucket bucket = buckets.get(group + ":" + client, key -> Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit).refillGreedy(limit, period)).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", Long.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
            response.setHeader("Retry-After", Long.toString(seconds));
            metrics.counter("gateway.rate.limit.rejected", "group", group).increment();
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Too many requests; retry after the indicated delay");
            return;
        }
        chain.doFilter(request, response);
    }
}
