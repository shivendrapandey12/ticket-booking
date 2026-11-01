package com.shivendra.ticketbooking.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingConfirmedEvent(Long bookingId, String userName, String userEmail, String movieTitle,
                                    String theatreName, LocalDateTime showTime, List<String> seats,
                                    BigDecimal amount) {}
