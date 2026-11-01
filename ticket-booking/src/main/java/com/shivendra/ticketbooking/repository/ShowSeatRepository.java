package com.shivendra.ticketbooking.repository;

import com.shivendra.ticketbooking.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
    List<ShowSeat> findByShowIdOrderBySeatNumber(Long showId);
    List<ShowSeat> findByShowIdAndSeatNumberIn(Long showId, List<String> seatNumbers);
}
