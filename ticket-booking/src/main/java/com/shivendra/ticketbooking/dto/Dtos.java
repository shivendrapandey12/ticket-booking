package com.shivendra.ticketbooking.dto;

import com.shivendra.ticketbooking.entity.Booking;
import com.shivendra.ticketbooking.entity.BookingStatus;
import com.shivendra.ticketbooking.entity.ShowSeat;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** All request/response payloads in one place to keep the project easy to navigate. */
public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(@NotBlank String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {}

    // ---- catalog ----
    public record MovieRequest(@NotBlank String title, String language, String genre, @Positive int durationMinutes) {}

    public record ShowRequest(@NotNull Long movieId,
                              @NotBlank String theatreName,
                              @NotBlank String screenName,
                              @NotNull @Future LocalDateTime startTime,
                              @Min(1) @Max(26) int rows,
                              @Min(1) @Max(50) int seatsPerRow,
                              @NotNull @Positive BigDecimal price) {}

    public record ShowResponse(Long id, Long movieId, String movieTitle, String theatreName,
                               String screenName, LocalDateTime startTime) {}

    /** status is AVAILABLE, HELD (locked in Redis by someone) or BOOKED. */
    public record SeatResponse(String seatNumber, BigDecimal price, String status) {}

    // ---- booking ----
    public record HoldRequest(@NotEmpty @Size(max = 10, message = "max 10 seats per booking") List<@NotBlank String> seatNumbers) {}

    public record BookingResponse(Long id, BookingStatus status, String movieTitle, String theatreName,
                                  String screenName, LocalDateTime showTime, List<String> seats,
                                  BigDecimal totalAmount, Instant lockExpiresAt) {
        /** Must be called inside a transaction (touches lazy associations). */
        public static BookingResponse from(Booking b) {
            return new BookingResponse(
                    b.getId(), b.getStatus(),
                    b.getShow().getMovie().getTitle(),
                    b.getShow().getTheatreName(),
                    b.getShow().getScreenName(),
                    b.getShow().getStartTime(),
                    b.getSeats().stream().map(ShowSeat::getSeatNumber).sorted().toList(),
                    b.getTotalAmount(), b.getLockExpiresAt());
        }
    }
}
