package com.shivendra.ticketbooking.service;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.entity.RefreshToken;
import com.shivendra.ticketbooking.entity.Role;
import com.shivendra.ticketbooking.entity.User;
import com.shivendra.ticketbooking.exception.ApiException;
import com.shivendra.ticketbooking.repository.RefreshTokenRepository;
import com.shivendra.ticketbooking.repository.UserRepository;
import com.shivendra.ticketbooking.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final long refreshDays;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, @Value("${app.jwt.refresh-token-days}") long refreshDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email().toLowerCase())) {
            throw ApiException.conflict("Email already registered");
        }
        User u = new User();
        u.setName(req.name());
        u.setEmail(req.email().toLowerCase());
        u.setPassword(encoder.encode(req.password()));
        u.setRole(Role.USER);
        users.save(u);
        return issueTokens(u);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        if (!encoder.matches(req.password(), u.getPassword())) {
            throw new BadCredentialsException("bad credentials");
        }
        return issueTokens(u);
    }

    /** Refresh-token rotation: the old token is revoked and a brand new pair is issued. */
    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken old = refreshTokens.findByToken(req.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (old.isRevoked() || old.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }
        old.setRevoked(true);
        return issueTokens(old.getUser());
    }

    private AuthResponse issueTokens(User u) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(u);
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));
        refreshTokens.save(rt);
        return new AuthResponse(jwt.generateAccessToken(u), rt.getToken(), "Bearer", jwt.accessTokenSeconds());
    }
}
