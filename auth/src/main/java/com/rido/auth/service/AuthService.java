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
import java.util.List;

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

    // Micrometer
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter refreshSuccessCounter;
    private final Counter refreshReplayCounter;
    private final Counter loginLockoutCounter;
    private final Counter logoutSuccessCounter;
    private final Timer requestTimer;

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

        loginSuccessCounter = registry.counter("auth.login.success");
        loginFailureCounter = registry.counter("auth.login.failure");
        refreshSuccessCounter = registry.counter("auth.refresh.ok");
        refreshReplayCounter = registry.counter("auth.refresh.replay");
        loginLockoutCounter = registry.counter("auth.login.lockout");
        logoutSuccessCounter = registry.counter("auth.logout.ok");
        requestTimer = registry.timer("auth.request.duration");
    }

    // ==========================================================================================
    // REGISTER
    // ==========================================================================================
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

        // FIX: role must always be USER (auth roles are ADMIN, USER)
        user.setRole("USER");

        userRepository.save(user);

        log.info("auth_register_success", kv("username", username));
    }

    // ==========================================================================================
    // LOGIN
    // ==========================================================================================
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
            throw new AccountLockedException("Account locked");
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
                createTokens(user.getId(), user.getRole(), deviceId, ip, userAgent)
        );
    }

    // ==========================================================================================
    // REFRESH
    // ==========================================================================================
    public TokenResponse refresh(String refreshToken, String deviceId, String ip) {

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        Instant now = refreshTokenRepository.getDatabaseTime();

        if (rt.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token revoked");
        }

        if (now.isAfter(rt.getExpiresAt())) {
            refreshReplayCounter.increment();
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new TokenExpiredException("Refresh token expired");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        refreshSuccessCounter.increment();

        UserEntity user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

        return requestTimer.record(() ->
                createTokens(
                        rt.getUserId(),
                        user.getRole(),
                        rt.getDeviceId() != null ? rt.getDeviceId() : deviceId,
                        rt.getIp() != null ? rt.getIp() : ip,
                        rt.getUserAgent()
                )
        );
    }

    // ==========================================================================================
    // LOGOUT
    // ==========================================================================================
    public void logout(String refreshToken, String accessToken) {

        if (refreshToken == null)
            throw new InvalidCredentialsException("Missing refresh token");

        if (accessToken == null)
            throw new InvalidCredentialsException("Missing access token");

        tokenBlacklistService.blacklist(accessToken);

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (rt.isRevoked())
            throw new InvalidCredentialsException("Already revoked");

        tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        requestTimer.record(() -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            logoutSuccessCounter.increment();
        });
    }

    // ==========================================================================================
    // CREATE TOKENS
    // ==========================================================================================
    private TokenResponse createTokens(UUID userId, String role, String deviceId, String ip, String userAgent) {

        Instant now = Instant.now().plusMillis(5);
        Instant accessExp = now.plusSeconds(accessTtlSeconds);

        var keyPair = keyStore.getCurrentKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String kid = keyStore.getCurrentKid();

        String access = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setSubject(userId.toString())
                .claim("roles", List.of(role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(accessExp))
                .setId(UUID.randomUUID().toString())
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        String plain = UUID.randomUUID().toString();
        String hash = HashUtils.sha256(plain);

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(now.plusSeconds(refreshTtlSeconds));
        rt.setRevoked(false);
        rt.setDeviceId(deviceId);
        rt.setIp(ip);
        rt.setUserAgent(userAgent);
        rt.setJti(UUID.randomUUID());

        refreshTokenRepository.save(rt);

        return new TokenResponse(access, plain, accessTtlSeconds);
    }

    // ==========================================================================================
    // TRACE TAGS
    // ==========================================================================================
    private void tagCurrentSpan(UUID userId, String deviceId, String ip, UUID jti) {
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) return;

        if (userId != null) span.setAttribute("auth.user_id", userId.toString());
        if (deviceId != null) span.setAttribute("auth.device_id", deviceId);
        if (ip != null) span.setAttribute("auth.ip", ip);
        if (jti != null) span.setAttribute("auth.jti", jti.toString());
    }
}
