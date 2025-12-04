package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.DeviceMismatchException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.TokenExpiredException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.util.HashUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TracingService tracingService;
    private final AuditLogService auditLogService;

    // Micrometer
    private final Counter refreshSuccessCounter;
    private final Counter refreshReplayCounter;
    private final Timer requestTimer;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            TokenService tokenService,
            TracingService tracingService,
            AuditLogService auditLogService,
            MeterRegistry registry
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.tracingService = tracingService;
        this.auditLogService = auditLogService;

        this.refreshSuccessCounter = registry.counter("auth.refresh.ok");
        this.refreshReplayCounter = registry.counter("auth.refresh.replay");
        this.requestTimer = registry.timer("auth.request.duration");
    }

    public TokenResponse refresh(String refreshToken, String deviceId, String ip, String userAgent) {

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        tracingService.tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        // Device Security Check
        if (!java.util.Objects.equals(rt.getDeviceId(), deviceId) || 
            !java.util.Objects.equals(rt.getUserAgent(), userAgent)) {
            
            UserEntity user = userRepository.findById(rt.getUserId()).orElse(null);
            String username = user != null ? user.getUsername() : "unknown";
            
            auditLogService.logDeviceMismatch(
                rt.getUserId(), 
                username, 
                ip, 
                rt.getDeviceId(), 
                deviceId, 
                rt.getUserAgent(), 
                userAgent
            );
            
            throw new DeviceMismatchException("Device or User-Agent mismatch detected");
        }

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

        // Audit logging
        auditLogService.logRefresh(rt.getUserId(), user.getUsername(), ip, deviceId);

        return requestTimer.record(() ->
                tokenService.createTokens(
                        rt.getUserId(),
                        user.getRole(),
                        rt.getDeviceId() != null ? rt.getDeviceId() : deviceId,
                        rt.getIp() != null ? rt.getIp() : ip,
                        rt.getUserAgent()
                )
        );
    }
}
