package com.shivendra.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "show_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_number"}))
@Getter @Setter @NoArgsConstructor
public class ShowSeat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Show show;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** Optimistic locking: second layer of defence against double booking. */
    @Version
    private Long version;
}
