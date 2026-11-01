package com.shivendra.ticketbooking.repository;

import com.shivendra.ticketbooking.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {}
