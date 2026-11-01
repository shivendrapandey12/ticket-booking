package com.shivendra.ticketbooking.repository;

import com.shivendra.ticketbooking.entity.Booking;
import com.shivendra.ticketbooking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByStatusAndLockExpiresAtBefore(BookingStatus status, Instant time);
}
