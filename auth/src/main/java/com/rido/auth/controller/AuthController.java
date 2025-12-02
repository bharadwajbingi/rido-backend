package com.rido.auth.controller;

import com.rido.auth.dto.*;
import com.rido.auth.exception.*;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.service.AuthService;
import com.rido.auth.service.TokenBlacklistService;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.rate.RateLimiterService;
import com.rido.auth.service.LoginAttemptService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.micrometer.core.annotation.Timed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;


@RestController
@RequestMapping("/auth")
@Timed(value = "auth.request.duration", extraTags = {"module", "auth"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtKeyStore keyStore;
    private final RateLimiterService rateLimiter;
    private final LoginAttemptService loginAttemptService;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            TokenBlacklistService tokenBlacklistService,
            RefreshTokenRepository refreshTokenRepository,
            JwtKeyStore keyStore,
            RateLimiterService rateLimiter,
            LoginAttemptService loginAttemptService
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.keyStore = keyStore;
        this.rateLimiter = rateLimiter;
        this.loginAttemptService = loginAttemptService;
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

        log.info("auth_register_request",
                kv("username", req.getUsername()),
                kv("ip", ip)
        );

        rateLimiter.checkRateLimit("register:" + ip, 10, 60);

        authService.register(req.getUsername(), req.getPassword(), ip);

        log.info("auth_register_success",
                kv("username", req.getUsername()),
                kv("ip", ip)
        );

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
        String username = req.getUsername();
        String deviceId = request.getHeader("X-Device-Id");
        String userAgent = request.getHeader("User-Agent");

        log.info("auth_login_attempt",
                kv("username", username),
                kv("ip", ip),
                kv("deviceId", deviceId),
                kv("userAgent", userAgent)
        );

        loginAttemptService.ensureNotLocked(username);

        try {

            TokenResponse resp = authService.login(
                    username,
                    req.getPassword(),
                    deviceId,
                    ip,
                    userAgent
            );

            rateLimiter.checkRateLimit("login:ip:" + ip, 50, 60);

            log.info("auth_login_success",
                    kv("username", username),
                    kv("ip", ip),
                    kv("deviceId", deviceId)
            );

            return ResponseEntity.ok(resp);

        } catch (InvalidCredentialsException e) {

            log.warn("auth_login_failure",
                    kv("username", username),
                    kv("ip", ip)
            );

            loginAttemptService.onFailure(
                    username,
                    ip,
                    userRepository.findByUsername(username).orElse(null)
            );

            rateLimiter.checkRateLimit("login:user:" + username, 10, 300);

            throw e;

        } catch (AccountLockedException e) {

            log.error("auth_login_account_locked",
                    kv("username", username),
                    kv("ip", ip)
            );

            rateLimiter.checkRateLimit("login:user:" + username, 10, 300);
            throw e;
        }
    }

    // =====================================================
    // REFRESH TOKEN (ROTATION)
    // =====================================================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody RefreshRequest body,
            HttpServletRequest request
    ) {
        if (body.refreshToken() == null) {
            log.warn("auth_refresh_missing_token");
            throw new InvalidCredentialsException("refreshToken missing");
        }

        String ip = request.getRemoteAddr();
        String deviceId = request.getHeader("X-Device-Id");

        log.info("auth_refresh_attempt",
                kv("deviceId", deviceId),
                kv("ip", ip)
        );

        rateLimiter.checkRateLimit("refresh:" + ip, 20, 60);

        TokenResponse resp = authService.refresh(body.refreshToken(), deviceId, ip);

        log.info("auth_refresh_success",
                kv("ip", ip),
                kv("deviceId", deviceId)
        );

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
            log.warn("auth_logout_missing_refreshToken");
            throw new InvalidCredentialsException("refreshToken missing");
        }

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        log.info("auth_logout_attempt");

        authService.logout(req.refreshToken(), accessToken);

        log.info("auth_logout_success");

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // =====================================================
    // /me
    // =====================================================
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(name = "X-User-ID", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            log.warn("auth_me_unauthorized_missing_userId");
            throw new InvalidCredentialsException("Unauthorized");
        }

        log.info("auth_me_request", kv("userId", userId));

        return userRepository.findById(UUID.fromString(userId))
                .map(u -> ResponseEntity.ok(Map.of(
                        "id", u.getId().toString(),
                        "username", u.getUsername()
                )))
                .orElseGet(() -> {
                    log.warn("auth_me_user_not_found", kv("userId", userId));
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "user not found"));
                });
    }

    // =====================================================
    // CHECK USERNAME AVAILABILITY
    // =====================================================
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {

        boolean exists = userRepository.findByUsername(username).isPresent();

        log.info("auth_check_username",
                kv("username", username),
                kv("available", !exists)
        );

        return ResponseEntity.ok(Map.of("available", !exists));
    }

    // =====================================================
    // LIST ACTIVE SESSIONS
    // =====================================================
    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(
            @RequestHeader("X-User-ID") String userIdHeader
    ) {
        UUID userId = UUID.fromString(userIdHeader);

        var sessions = refreshTokenRepository
                .findActiveByUserId(userId)
                .stream()
                .sorted((a,b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(sessions);
    }

    // =====================================================
    // REVOKE ALL SESSIONS
    // =====================================================
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<?> revokeAllSessions(
            @RequestHeader("X-User-ID") String userIdHeader
    ) {
        UUID userId = UUID.fromString(userIdHeader);

        refreshTokenRepository.revokeAllForUser(userId);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // =====================================================
    // REVOKE ONE SESSION
    // =====================================================
    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<?> revokeSession(
            @RequestHeader("X-User-ID") String userIdHeader,
            @PathVariable UUID sessionId
    ) {
        UUID userId = UUID.fromString(userIdHeader);

        RefreshTokenEntity session = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidCredentialsException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new InvalidCredentialsException("Unauthorized session access");
        }

        refreshTokenRepository.revokeOne(sessionId);

        return ResponseEntity.ok(Map.of("status", "revoked"));
    }



    // =====================================================
    // HELPERS
    // =====================================================
    private SessionDTO toDTO(RefreshTokenEntity r) {
        Instant created =
                r.getCreatedAt() != null ? r.getCreatedAt() : Instant.now();

        return new SessionDTO(
                r.getId(),
                r.getDeviceId(),
                r.getIp(),
                r.getUserAgent(),
                r.isRevoked(),
                created,
                r.getExpiresAt()
        );
    }


}
