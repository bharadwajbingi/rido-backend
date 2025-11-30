package com.rido.auth.service;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.*;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.util.HashUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import io.opentelemetry.api.trace.Span;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtKeyStore keyStore;

    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    private final int maxFailedAttempts;
    private final long lockoutDurationSeconds;

    // Micrometer metrics
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter refreshSuccessCounter;
    private final Counter refreshReplayCounter;
    private final Counter loginLockoutCounter;
    private final Counter logoutSuccessCounter;

    private final Timer requestTimer;   // ⬅ REQUIRED FOR PHASE-2

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            JwtConfig jwtConfig,
            @Value("${auth.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${auth.login.lockout-duration-seconds:300}") long lockoutDurationSeconds,
            TokenBlacklistService tokenBlacklistService,
            JwtKeyStore keyStore,
            MeterRegistry registry
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.keyStore = keyStore;

        this.accessTtlSeconds = jwtConfig.accessTokenTtlSeconds();
        this.refreshTtlSeconds = jwtConfig.refreshTokenTtlSeconds();

        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationSeconds = lockoutDurationSeconds;

        this.loginSuccessCounter = registry.counter("auth.login.success");
        this.loginFailureCounter = registry.counter("auth.login.failure");
        this.refreshSuccessCounter = registry.counter("auth.refresh.ok");
        this.refreshReplayCounter = registry.counter("auth.refresh.replay");
        this.loginLockoutCounter = registry.counter("auth.login.lockout");
        this.logoutSuccessCounter = registry.counter("auth.logout.ok");

        this.requestTimer = registry.timer("auth.request.duration");  // ⬅ ADDED
    }

    // ============================================================
    // REGISTER
    // ============================================================
    public void register(String username, String password) {

        log.info("auth_register_attempt", kv("username", username));

        userRepository.findByUsername(username)
                .ifPresent(u -> {
                    log.warn("auth_register_failed",
                            kv("username", username),
                            kv("reason", "username_taken"));
                    throw new UsernameAlreadyExistsException("Username already exists");
                });

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("user");

        userRepository.save(user);

        log.info("auth_register_success", kv("username", username));
    }

    // ============================================================
    // LOGIN
    // ============================================================
    public TokenResponse login(String username, String password, String deviceId, String ip, String userAgent) {

        loginAttemptService.ensureNotLocked(username);

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            loginFailureCounter.increment();
            loginAttemptService.onFailure(username, ip, null);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        UserEntity user = userOpt.get();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            loginFailureCounter.increment();
            loginLockoutCounter.increment();
            throw new AccountLockedException("Account locked until: " + user.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginFailureCounter.increment();
            loginAttemptService.onFailure(username, ip, user);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        loginAttemptService.onSuccess(username, ip, user);
        loginSuccessCounter.increment();

        tagCurrentSpan(user.getId(), deviceId, ip, null);

        return requestTimer.record(() ->
                createTokens(user.getId(), deviceId, ip, userAgent)
        );
    }

    // ============================================================
    // REFRESH (ROTATION + REPLAY DETECTION)
    // ============================================================
    public TokenResponse refresh(String refreshToken, String deviceId, String ip) {

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        if (rt.isRevoked()) {
            log.error("auth_refresh_replay_detected", kv("userId", rt.getUserId()));
            refreshReplayCounter.increment();
            refreshTokenRepository.revokeAllForUser(rt.getUserId());
            throw new ReplayDetectedException("Refresh token replay detected");
        }

        Instant now = refreshTokenRepository.getDatabaseTime();

        if (rt.getExpiresAt().minusSeconds(5).isBefore(now)) {
            log.warn("auth_refresh_expired", kv("userId", rt.getUserId()));
            refreshReplayCounter.increment();
            refreshTokenRepository.revokeAllForUser(rt.getUserId());
            throw new TokenExpiredException("Refresh token expired");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        refreshSuccessCounter.increment();

        String finalDevice = rt.getDeviceId() != null ? rt.getDeviceId() : deviceId;
        String finalIp = rt.getIp() != null ? rt.getIp() : ip;

        return requestTimer.record(() ->
                createTokens(rt.getUserId(), finalDevice, finalIp, rt.getUserAgent())
        );
    }

    // ============================================================
    // LOGOUT
    // ============================================================
    public void logout(String refreshToken, String accessToken) {

        if (refreshToken == null || refreshToken.isBlank())
            throw new InvalidCredentialsException("Missing refresh token");

        if (accessToken == null || accessToken.isBlank())
            throw new InvalidCredentialsException("Missing access token");

        tokenBlacklistService.blacklist(accessToken);

        String hash = HashUtils.sha256(refreshToken);
        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (rt.isRevoked())
            throw new InvalidCredentialsException("Refresh token already revoked");

        tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        requestTimer.record(() -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            logoutSuccessCounter.increment();
        });
    }

    // ============================================================
    // CREATE TOKENS — RS256 + KID
    // ============================================================
    private TokenResponse createTokens(UUID userId, String deviceId, String ip, String userAgent) {

        Instant now = Instant.now();
        Instant accessExp = now.plusSeconds(accessTtlSeconds);

        var keyPair = keyStore.getCurrentKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String kid = keyStore.getCurrentKid();

        // Create access token
        String accessToken = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setHeaderParam("typ", "JWT")
                .setSubject(userId.toString())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(accessExp))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        // Opaque refresh token
        String refreshPlain = UUID.randomUUID().toString();
        String refreshHash = HashUtils.sha256(refreshPlain);

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setTokenHash(refreshHash);
        rt.setExpiresAt(now.plusSeconds(refreshTtlSeconds));
        rt.setRevoked(false);
        rt.setDeviceId(deviceId);
        rt.setIp(ip);
        rt.setUserAgent(userAgent);
        rt.setJti(UUID.randomUUID());

        refreshTokenRepository.save(rt);

        return new TokenResponse(accessToken, refreshPlain, accessTtlSeconds);
    }

    // ============================================================
    // TRACE TAGGING
    // ============================================================
    private void tagCurrentSpan(UUID userId, String deviceId, String ip, UUID jti) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) return;

        if (userId != null) span.setAttribute("auth.user_id", userId.toString());
        if (deviceId != null) span.setAttribute("auth.device_id", deviceId);
        if (ip != null) span.setAttribute("auth.ip", ip);
        if (jti != null) span.setAttribute("auth.jti", jti.toString());
    }
}
