package com.shivendra.ticketbooking.repository;

import com.shivendra.ticketbooking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByMovieIdOrderByStartTime(Long movieId);
}
