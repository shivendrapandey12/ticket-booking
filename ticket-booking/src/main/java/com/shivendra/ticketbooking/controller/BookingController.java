package com.shivendra.ticketbooking.controller;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.security.AuthUser;
import com.shivendra.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) { this.bookings = bookings; }

    /** Step 1: pick seats. They are locked for 5 minutes. */
    @PostMapping("/shows/{showId}/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@AuthenticationPrincipal AuthUser user, @PathVariable Long showId,
                                @Valid @RequestBody HoldRequest req) {
        return bookings.hold(user, showId, req);
    }

    /** Step 2: pay. Send a unique Idempotency-Key header; retrying with the same key is safe. */
    @PostMapping("/bookings/{bookingId}/pay")
    public BookingResponse pay(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return bookings.pay(user, bookingId, idempotencyKey);
    }

    @DeleteMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId) {
        bookings.cancelHold(user, bookingId);
    }

    @GetMapping("/bookings/me")
    public List<BookingResponse> mine(@AuthenticationPrincipal AuthUser user) { return bookings.myBookings(user); }
}
