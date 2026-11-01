package com.shivendra.ticketbooking.service;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.entity.*;
import com.shivendra.ticketbooking.exception.ApiException;
import com.shivendra.ticketbooking.repository.MovieRepository;
import com.shivendra.ticketbooking.repository.ShowRepository;
import com.shivendra.ticketbooking.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogService {

    private final MovieRepository movies;
    private final ShowRepository shows;
    private final ShowSeatRepository seats;
    private final SeatLockService seatLocks;

    public CatalogService(MovieRepository movies, ShowRepository shows, ShowSeatRepository seats,
                          SeatLockService seatLocks) {
        this.movies = movies;
        this.shows = shows;
        this.seats = seats;
        this.seatLocks = seatLocks;
    }

    @Transactional
    public Movie createMovie(MovieRequest r) {
        Movie m = new Movie();
        m.setTitle(r.title());
        m.setLanguage(r.language());
        m.setGenre(r.genre());
        m.setDurationMinutes(r.durationMinutes());
        return movies.save(m);
    }

    public List<Movie> listMovies() { return movies.findAll(); }

    /** Creates a show and generates its seat grid: rows A.. and seat numbers 1..N (A1, A2, B1 ...). */
    @Transactional
    public ShowResponse createShow(ShowRequest r) {
        Movie movie = movies.findById(r.movieId())
                .orElseThrow(() -> ApiException.notFound("Movie not found: " + r.movieId()));
        Show s = new Show();
        s.setMovie(movie);
        s.setTheatreName(r.theatreName());
        s.setScreenName(r.screenName());
        s.setStartTime(r.startTime());
        shows.save(s);

        List<ShowSeat> grid = new ArrayList<>();
        for (int row = 0; row < r.rows(); row++) {
            for (int n = 1; n <= r.seatsPerRow(); n++) {
                ShowSeat seat = new ShowSeat();
                seat.setShow(s);
                seat.setSeatNumber("" + (char) ('A' + row) + n);
                seat.setPrice(r.price());
                grid.add(seat);
            }
        }
        seats.saveAll(grid);
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> showsForMovie(Long movieId) {
        return shows.findByMovieIdOrderByStartTime(movieId).stream().map(this::toResponse).toList();
    }

    /** Seat map: DB says BOOKED/AVAILABLE, Redis tells us which AVAILABLE seats are currently HELD. */
    @Transactional(readOnly = true)
    public List<SeatResponse> seatMap(Long showId) {
        if (!shows.existsById(showId)) throw ApiException.notFound("Show not found: " + showId);
        return seats.findByShowIdOrderBySeatNumber(showId).stream().map(s -> {
            String status = s.getStatus() == SeatStatus.BOOKED ? "BOOKED"
                    : seatLocks.isHeld(showId, s.getSeatNumber()) ? "HELD" : "AVAILABLE";
            return new SeatResponse(s.getSeatNumber(), s.getPrice(), status);
        }).toList();
    }

    private ShowResponse toResponse(Show s) {
        return new ShowResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(),
                s.getTheatreName(), s.getScreenName(), s.getStartTime());
    }
}
