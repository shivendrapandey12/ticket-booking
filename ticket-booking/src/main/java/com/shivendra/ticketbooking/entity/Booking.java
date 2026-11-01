package com.shivendra.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Show show;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "booking_seats")
    private List<ShowSeat> seats = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    /** The Redis seat locks stop being valid at this time. */
    @Column(nullable = false)
    private Instant lockExpiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
