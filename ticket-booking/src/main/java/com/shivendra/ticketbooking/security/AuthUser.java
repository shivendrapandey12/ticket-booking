package com.shivendra.ticketbooking.security;

import com.shivendra.ticketbooking.entity.Role;

/** Principal stored in the SecurityContext (built from JWT claims, no DB hit per request). */
public record AuthUser(Long id, String email, Role role) {}
