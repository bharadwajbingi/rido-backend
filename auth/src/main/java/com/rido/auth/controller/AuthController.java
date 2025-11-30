package com.rido.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.LogoutRequest;
import com.rido.auth.dto.RefreshRequest;
import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.service.AuthService;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.service.TokenBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtKeyStore keyStore;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            JwtConfig jwtConfig,
            TokenBlacklistService tokenBlacklistService,
            RefreshTokenRepository refreshTokenRepository,
            JwtKeyStore keyStore
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.keyStore = keyStore;
    }

    // =========================
    // REGISTER
    // =========================
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            authService.register(req.getUsername(), req.getPassword());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (UsernameAlreadyExistsException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed"));
        }
    }

    // =========================
    // LOGIN
    // =========================
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request
    ) {
        try {
            String ip = request.getRemoteAddr();
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

        } catch (AccountLockedException ex) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", ex.getMessage()));

        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed"));
        }
    }

    // =========================
    // REFRESH
    // =========================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody RefreshRequest body,
            HttpServletRequest request,
            @RequestHeader(name = "Authorization", required = false) String authHeader
    ) {

        String refresh = body.refreshToken();
        if (refresh == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing refreshToken"));
        }

        String oldAccessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            oldAccessToken = authHeader.substring(7);
        }

        try {
            String ip = request.getRemoteAddr();
            String deviceId = request.getHeader("X-Device-Id");

            TokenResponse resp = authService.refresh(refresh, deviceId, ip);

            // if (oldAccessToken != null && !oldAccessToken.isBlank()) {
            //     tokenBlacklistService.blacklist(oldAccessToken);
            // }

            return ResponseEntity.ok(resp);

        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Refresh failed"));
        }
    }

    // =========================
    // LOGOUT
    // =========================
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody LogoutRequest body
    ) {

        String refresh = body.refreshToken();
        if (refresh == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing refreshToken"));
        }

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        try {
            authService.logout(refresh, accessToken);
            return ResponseEntity.ok(Map.of("status", "ok"));

        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Logout failed"));
        }
    }

    // =========================
    // FIXED /me ENDPOINT
    // =========================
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(name = "X-User-ID", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        return userRepository.findById(UUID.fromString(userId))
                .map(u -> ResponseEntity.ok(Map.of(
                        "id", u.getId().toString(),
                        "username", u.getUsername()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "user not found")));
    }

    // =========================
    // CHECK USERNAME
    // =========================
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("available", !exists));
    }

    // =========================
    // SESSION LIST
    // =========================
    @GetMapping("/sessions")
    public List<RefreshTokenEntity> sessions(@AuthenticationPrincipal UserEntity user) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
    }

    // =========================
    // SESSION REVOKE ALL
    // =========================
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<?> revokeAll(@AuthenticationPrincipal UserEntity user) {
        refreshTokenRepository.revokeAllForUser(user.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
