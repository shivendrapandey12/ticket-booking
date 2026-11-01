package com.shivendra.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique key => a retried request can never create (or charge) a second payment. */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
