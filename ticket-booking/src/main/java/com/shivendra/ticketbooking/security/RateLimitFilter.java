package com.shivendra.ticketbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis (INCR + EXPIRE).
 * Applied to booking endpoints. Keyed by user id when logged in, otherwise by IP.
 * Fails open if Redis is down so the API stays available.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final int limitPerMinute;

    public RateLimitFilter(StringRedisTemplate redis, int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/bookings") || path.endsWith("/hold"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String who = request.getHeader("Authorization") != null
                ? "auth:" + Integer.toHexString(request.getHeader("Authorization").hashCode())
                : "ip:" + request.getRemoteAddr();
        String key = "rate:" + who + ":" + (System.currentTimeMillis() / 60_000);

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(65));
            }
            if (count != null && count > limitPerMinute) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Rate limit exceeded. Try again in a minute.\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }
}
