package com.shivendra.ticketbooking;

import com.shivendra.ticketbooking.service.SeatLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    SeatLockService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        service = new SeatLockService(redis, 5);
    }

    @Test
    void locksAllSeatsWhenFree() {
        when(ops.setIfAbsent(eq("seat-lock:1:A1"), eq("7"), any(Duration.class))).thenReturn(true);
        when(ops.setIfAbsent(eq("seat-lock:1:A2"), eq("7"), any(Duration.class))).thenReturn(true);

        assertNull(service.tryLockAll(1L, List.of("A1", "A2"), 7L));
    }

    @Test
    void rollsBackEarlierLocksWhenOneSeatIsTaken() {
        when(ops.setIfAbsent(eq("seat-lock:1:A1"), eq("7"), any(Duration.class))).thenReturn(true);
        when(ops.setIfAbsent(eq("seat-lock:1:A2"), eq("7"), any(Duration.class))).thenReturn(false);

        String contested = service.tryLockAll(1L, List.of("A1", "A2"), 7L);

        assertEquals("A2", contested);
        // the lock already acquired for A1 must be released again
        verify(redis).execute(any(RedisScript.class), eq(List.of("seat-lock:1:A1")), eq("7"));
    }
}
