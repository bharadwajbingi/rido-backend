package com.rido.auth.service;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.util.HashUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    private final String jwtSecret;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    private final int maxFailedAttempts;
    private final long lockoutDurationSeconds;

    private Key signingKey;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            JwtConfig jwtConfig,
            @Value("${auth.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${auth.login.lockout-duration-seconds:300}") long lockoutDurationSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;

        this.jwtSecret = jwtConfig.secret();
        this.accessTtlSeconds = jwtConfig.accessTokenTtlSeconds();
        this.refreshTtlSeconds = jwtConfig.refreshTokenTtlSeconds();

        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationSeconds = lockoutDurationSeconds;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // =====================================================
    // REGISTER
    // =====================================================
    public void register(String username, String password) {
        userRepository.findByUsername(username)
                .ifPresent(u -> { throw new UsernameAlreadyExistsException("Username already exists"); });

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));  // Argon2
        user.setRole("user");

        userRepository.save(user);
    }

    // =====================================================
    // LOGIN
    // =====================================================
    public TokenResponse login(String username, String password, String deviceId, String ip) {

        loginAttemptService.ensureNotLocked(username);

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            loginAttemptService.onFailure(username, ip, null);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        UserEntity user = userOpt.get();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException("Account locked until: " + user.getLockedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.onFailure(username, ip, user);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        loginAttemptService.onSuccess(username, ip, user);

        return createTokens(user.getId(), deviceId, ip);
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================
    public TokenResponse refresh(String refreshToken, String deviceId, String ip) {

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (rt.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(rt.getUserId());
            throw new InvalidCredentialsException("Refresh token revoked");
        }

        Instant now = refreshTokenRepository.getDatabaseTime();
        Instant expCheck = rt.getExpiresAt().minusSeconds(5);

        if (expCheck.isBefore(now)) {
            refreshTokenRepository.revokeAllForUser(rt.getUserId());
            throw new InvalidCredentialsException("Refresh token expired");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        return createTokens(rt.getUserId(), deviceId, ip);
    }

    // =====================================================
    // LOGOUT
    // =====================================================
    public void logout(String refreshToken) {
        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        refreshTokenRepository.revokeAllForUser(rt.getUserId());
    }

    // =====================================================
    // TOKEN CREATION
    // =====================================================
    private TokenResponse createTokens(UUID userId, String deviceId, String ip) {

        Instant now = Instant.now();

        Instant accessExp = now.plusSeconds(accessTtlSeconds);
        String accessToken = Jwts.builder()
                .setSubject(userId.toString())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(accessExp))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = UUID.randomUUID().toString();
        String refreshHash = HashUtils.sha256(refreshToken);
        Instant refreshExp = now.plusSeconds(refreshTtlSeconds);

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setTokenHash(refreshHash);
        rt.setExpiresAt(refreshExp);
        rt.setRevoked(false);
        rt.setDeviceId(deviceId);
        rt.setIp(ip);

        refreshTokenRepository.save(rt);

        return new TokenResponse(accessToken, refreshToken, accessTtlSeconds);
    }
}
