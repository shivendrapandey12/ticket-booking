package com.shivendra.ticketbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Distributed seat locks in Redis.
 *
 *   key   = seat-lock:{showId}:{seatNumber}
 *   value = userId of the holder
 *   TTL   = app.booking.seat-lock-minutes  (lock disappears on its own if the user never pays)
 *
 * Acquire uses SET key value NX EX ttl, which is atomic, so two users can never both get the same seat.
 * Release uses a Lua compare-and-delete so a user can only ever release a lock that is still theirs.
 */
@Service
public class SeatLockService {

    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SeatLockService(StringRedisTemplate redis, @Value("${app.booking.seat-lock-minutes}") long minutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(minutes);
    }

    public Duration ttl() { return ttl; }

    static String key(Long showId, String seat) { return "seat-lock:" + showId + ":" + seat; }

    /** All-or-nothing: locks every seat, or none. Returns the seat that was already held, or null on success. */
    public String tryLockAll(Long showId, List<String> seatNumbers, Long userId) {
        List<String> acquired = new ArrayList<>();
        for (String seat : seatNumbers) {
            Boolean ok = redis.opsForValue().setIfAbsent(key(showId, seat), String.valueOf(userId), ttl);
            if (Boolean.TRUE.equals(ok)) {
                acquired.add(seat);
            } else {
                releaseAll(showId, acquired, userId);   // roll back the partial lock
                return seat;
            }
        }
        return null;
    }

    public boolean isHeld(Long showId, String seat) {
        return Boolean.TRUE.equals(redis.hasKey(key(showId, seat)));
    }

    public boolean isHeldBy(Long showId, String seat, Long userId) {
        return String.valueOf(userId).equals(redis.opsForValue().get(key(showId, seat)));
    }

    public void releaseAll(Long showId, List<String> seatNumbers, Long userId) {
        for (String seat : seatNumbers) {
            redis.execute(RELEASE_IF_OWNER, List.of(key(showId, seat)), String.valueOf(userId));
        }
    }
}
