package com.rido.auth.controller;

import com.rido.auth.dto.*;
import com.rido.auth.exception.*;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.service.AuthService;
import com.rido.auth.service.TokenBlacklistService;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.rate.RateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.micrometer.core.annotation.Timed;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Timed(value = "auth.request.duration", extraTags = { "module", "auth" })
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtKeyStore keyStore;
    private final RateLimiterService rateLimiter;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            TokenBlacklistService tokenBlacklistService,
            RefreshTokenRepository refreshTokenRepository,
            JwtKeyStore keyStore,
            RateLimiterService rateLimiter
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.keyStore = keyStore;
        this.rateLimiter = rateLimiter;
    }

    // =====================================================
    // REGISTER
    // =====================================================
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        rateLimiter.checkRateLimit("register:" + ip, 10, 60);

        authService.register(req.getUsername(), req.getPassword());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // =====================================================
    // LOGIN
    // =====================================================
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        rateLimiter.checkRateLimit("login:" + ip, 5, 60);

        String deviceId = request.getHeader("X-Device-Id");
        String userAgent = request.getHeader("User-Agent");

        TokenResponse resp = authService.login(
                req.getUsername(),
                req.getPassword(),
                deviceId,
                ip,
                userAgent
        );

        return ResponseEntity.ok(resp);
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody RefreshRequest body,
            HttpServletRequest request
    ) {
        if (body.refreshToken() == null) {
            throw new InvalidCredentialsException("refreshToken missing");
        }

        String ip = request.getRemoteAddr();
        rateLimiter.checkRateLimit("refresh:" + ip, 20, 60);

        String deviceId = request.getHeader("X-Device-Id");

        TokenResponse resp = authService.refresh(body.refreshToken(), deviceId, ip);
        return ResponseEntity.ok(resp);
    }

    // =====================================================
    // LOGOUT
    // =====================================================
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody LogoutRequest req
    ) {
        if (req.refreshToken() == null) {
            throw new InvalidCredentialsException("refreshToken missing");
        }

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        authService.logout(req.refreshToken(), accessToken);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // =====================================================
    // /me ENDPOINT (Gateway injects X-User-ID)
    // =====================================================
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(name = "X-User-ID", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidCredentialsException("Unauthorized");
        }

        return userRepository.findById(UUID.fromString(userId))
                .map(u -> ResponseEntity.ok(Map.of(
                        "id", u.getId().toString(),
                        "username", u.getUsername()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "user not found")));
    }

    // =====================================================
    // CHECK USERNAME
    // =====================================================
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("available", !exists));
    }

    // =====================================================
    // ACTIVE SESSIONS LIST
    // =====================================================
    @GetMapping("/sessions")
    public List<RefreshTokenEntity> sessions(@AuthenticationPrincipal UserEntity user) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
    }

    // =====================================================
    // REVOKE ALL SESSIONS
    // =====================================================
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<?> revokeAll(@AuthenticationPrincipal UserEntity user) {
        refreshTokenRepository.revokeAllForUser(user.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
