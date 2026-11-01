package com.shivendra.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shows")
@Getter @Setter @NoArgsConstructor
public class Show {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Movie movie;

    @Column(nullable = false)
    private String theatreName;

    @Column(nullable = false)
    private String screenName;

    @Column(nullable = false)
    private LocalDateTime startTime;
}
