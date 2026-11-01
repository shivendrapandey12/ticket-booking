package com.shivendra.ticketbooking.controller;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.entity.Movie;
import com.shivendra.ticketbooking.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public, read-only browsing endpoints. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/movies")
    public List<Movie> movies() { return catalog.listMovies(); }

    @GetMapping("/movies/{movieId}/shows")
    public List<ShowResponse> shows(@PathVariable Long movieId) { return catalog.showsForMovie(movieId); }

    @GetMapping("/shows/{showId}/seats")
    public List<SeatResponse> seats(@PathVariable Long showId) { return catalog.seatMap(showId); }
}
