package com.shivendra.ticketbooking.service;

import com.shivendra.ticketbooking.dto.Dtos.BookingResponse;
import com.shivendra.ticketbooking.dto.Dtos.HoldRequest;
import com.shivendra.ticketbooking.entity.*;
import com.shivendra.ticketbooking.event.BookingConfirmedEvent;
import com.shivendra.ticketbooking.event.BookingEventProducer;
import com.shivendra.ticketbooking.exception.ApiException;
import com.shivendra.ticketbooking.repository.*;
import com.shivendra.ticketbooking.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Booking flow, with three layers of protection against double booking:
 *   1. Redis SET NX seat lock (fast, distributed, auto-expires after 5 min)        -> hold()
 *   2. Seat status check inside the DB transaction                                  -> confirm()
 *   3. JPA optimistic locking (@Version on ShowSeat) as the final safety net        -> confirm()
 *
 * Payment is idempotent: the Idempotency-Key is unique in the payments table, so a retried
 * request returns the original result and never charges twice.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final SeatLockService seatLocks;
    private final BookingEventProducer events;
    private final TransactionTemplate tx;

    public BookingService(ShowRepository shows, ShowSeatRepository showSeats, BookingRepository bookings,
                          PaymentRepository payments, UserRepository users, SeatLockService seatLocks,
                          BookingEventProducer events, PlatformTransactionManager txManager) {
        this.shows = shows;
        this.showSeats = showSeats;
        this.bookings = bookings;
        this.payments = payments;
        this.users = users;
        this.seatLocks = seatLocks;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ HOLD

    public BookingResponse hold(AuthUser user, Long showId, HoldRequest req) {
        List<String> seatNumbers = req.seatNumbers().stream().map(String::trim).map(String::toUpperCase).distinct().toList();

        String contested = seatLocks.tryLockAll(showId, seatNumbers, user.id());
        if (contested != null) {
            throw ApiException.conflict("Seat " + contested + " is currently held by another user");
        }
        try {
            return tx.execute(status -> createPendingBooking(user, showId, seatNumbers));
        } catch (RuntimeException e) {
            seatLocks.releaseAll(showId, seatNumbers, user.id());   // never leave orphan locks behind
            throw e;
        }
    }

    private BookingResponse createPendingBooking(AuthUser au, Long showId, List<String> seatNumbers) {
        Show show = shows.findById(showId).orElseThrow(() -> ApiException.notFound("Show not found: " + showId));
        if (show.getStartTime().isBefore(java.time.LocalDateTime.now())) {
            throw ApiException.badRequest("This show has already started");
        }
        List<ShowSeat> seats = showSeats.findByShowIdAndSeatNumberIn(showId, seatNumbers);
        if (seats.size() != seatNumbers.size()) {
            throw ApiException.badRequest("One or more seats do not exist for this show");
        }
        for (ShowSeat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                throw ApiException.conflict("Seat " + s.getSeatNumber() + " is already booked");
            }
        }
        Booking b = new Booking();
        b.setUser(users.getReferenceById(au.id()));
        b.setShow(show);
        b.setSeats(new java.util.ArrayList<>(seats));
        b.setTotalAmount(seats.stream().map(ShowSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add));
        b.setLockExpiresAt(Instant.now().plus(seatLocks.ttl()));
        bookings.save(b);
        return BookingResponse.from(b);
    }

    // ------------------------------------------------------------------- PAY

    private record PayResult(BookingResponse response, BookingConfirmedEvent event, Long showId, List<String> seats) {}

    public BookingResponse pay(AuthUser user, Long bookingId, String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("Idempotency-Key must be 1-100 characters");
        }
        // Retry of a request we already processed -> return the original result, charge nothing.
        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return tx.execute(s -> replay(existing.get().getId(), user, bookingId));
        }

        PayResult result;
        try {
            result = tx.execute(s -> confirm(user, bookingId, idempotencyKey));
        } catch (DataIntegrityViolationException dup) {
            // Two identical requests raced; the unique key let only one through. Return its result.
            var winner = payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> dup);
            return tx.execute(s -> replay(winner.getId(), user, bookingId));
        }

        // Only after the DB commit succeeded:
        seatLocks.releaseAll(result.showId(), result.seats(), user.id());
        events.publish(result.event());
        return result.response();
    }

    private PayResult confirm(AuthUser au, Long bookingId, String key) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
        if (!b.getUser().getId().equals(au.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This booking belongs to another user");
        }
        if (b.getStatus() != BookingStatus.PENDING) {
            throw ApiException.conflict("Booking is already " + b.getStatus());
        }
        Long showId = b.getShow().getId();
        List<String> seatNumbers = b.getSeats().stream().map(ShowSeat::getSeatNumber).toList();

        // Layer 1: do we still own the Redis locks (i.e. did the 5-minute hold run out)?
        boolean stillHeld = b.getLockExpiresAt().isAfter(Instant.now())
                && seatNumbers.stream().allMatch(sn -> seatLocks.isHeldBy(showId, sn, au.id()));
        if (!stillHeld) {
            throw ApiException.conflict("Seat hold expired. Please select seats again.");
        }

        // Layers 2 + 3: status check, then flip to BOOKED. @Version makes a concurrent update fail the commit.
        for (ShowSeat s : b.getSeats()) {
            if (s.getStatus() != SeatStatus.AVAILABLE) {
                throw ApiException.conflict("Seat " + s.getSeatNumber() + " is already booked");
            }
            s.setStatus(SeatStatus.BOOKED);
        }
        showSeats.saveAllAndFlush(b.getSeats());

        // Payment simulation (a real gateway call would go here, using the same idempotency key).
        Payment p = new Payment();
        p.setIdempotencyKey(key);
        p.setBooking(b);
        p.setAmount(b.getTotalAmount());
        p.setStatus("SUCCESS");
        payments.saveAndFlush(p);

        b.setStatus(BookingStatus.CONFIRMED);
        bookings.save(b);

        BookingResponse response = BookingResponse.from(b);
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                b.getId(), b.getUser().getName(), b.getUser().getEmail(), response.movieTitle(),
                response.theatreName(), response.showTime(), response.seats(), response.totalAmount());
        return new PayResult(response, event, showId, seatNumbers);
    }

    private BookingResponse replay(Long paymentId, AuthUser au, Long bookingId) {
        Payment p = payments.findById(paymentId).orElseThrow();
        Booking b = p.getBooking();
        if (!b.getId().equals(bookingId) || !b.getUser().getId().equals(au.id())) {
            throw ApiException.conflict("Idempotency-Key was already used for a different request");
        }
        return BookingResponse.from(b);
    }

    // ---------------------------------------------------------------- CANCEL

    public void cancelHold(AuthUser user, Long bookingId) {
        record Released(Long showId, List<String> seats) {}
        Released r = tx.execute(s -> {
            Booking b = bookings.findById(bookingId)
                    .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));
            if (!b.getUser().getId().equals(user.id())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "This booking belongs to another user");
            }
            if (b.getStatus() != BookingStatus.PENDING) {
                throw ApiException.conflict("Only a pending booking can be cancelled");
            }
            b.setStatus(BookingStatus.CANCELLED);
            return new Released(b.getShow().getId(), b.getSeats().stream().map(ShowSeat::getSeatNumber).toList());
        });
        seatLocks.releaseAll(r.showId(), r.seats(), user.id());
    }

    // ----------------------------------------------------------------- QUERY

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(AuthUser user) {
        return bookings.findByUserIdOrderByCreatedAtDesc(user.id()).stream().map(BookingResponse::from).toList();
    }

    // ---------------------------------------------------------------- EXPIRY

    /** Redis TTL already freed the seats; this just marks the abandoned bookings as EXPIRED in the DB. */
    @Scheduled(fixedDelayString = "PT30S")
    @Transactional
    public void expireStaleHolds() {
        List<Booking> stale = bookings.findByStatusAndLockExpiresAtBefore(BookingStatus.PENDING, Instant.now());
        stale.forEach(b -> b.setStatus(BookingStatus.EXPIRED));
        if (!stale.isEmpty()) log.info("Expired {} abandoned seat holds", stale.size());
    }
}
