package com.shivendra.ticketbooking.controller;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.entity.Movie;
import com.shivendra.ticketbooking.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** ADMIN only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CatalogService catalog;

    public AdminController(CatalogService catalog) { this.catalog = catalog; }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public Movie addMovie(@Valid @RequestBody MovieRequest req) { return catalog.createMovie(req); }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse addShow(@Valid @RequestBody ShowRequest req) { return catalog.createShow(req); }
}
