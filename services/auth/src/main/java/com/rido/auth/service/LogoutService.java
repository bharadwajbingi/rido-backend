package com.rido.auth.service;

import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.util.HashUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final TracingService tracingService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    // Micrometer
    private final Counter logoutSuccessCounter;
    private final Timer requestTimer;

    public LogoutService(
            RefreshTokenRepository refreshTokenRepository,
            TokenBlacklistService tokenBlacklistService,
            TracingService tracingService,
            AuditLogService auditLogService,
            UserRepository userRepository,
            MeterRegistry registry
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.tracingService = tracingService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;

        this.logoutSuccessCounter = registry.counter("auth.logout.ok");
        this.requestTimer = registry.timer("auth.request.duration");
    }

    /**
     * Logout user by revoking their refresh token.
     * 
     * FIX-AUTH-003: Added callingUserId parameter to verify session ownership.
     * Users can only logout their own sessions, not other users' sessions.
     * 
     * @param refreshToken The refresh token to revoke
     * @param accessToken The access token to blacklist
     * @param callingUserId The ID of the user making the request (from JWT)
     */
    public void logout(String refreshToken, String accessToken, UUID callingUserId) {

        if (refreshToken == null)
            throw new InvalidCredentialsException("Missing refresh token");

        if (accessToken == null)
            throw new InvalidCredentialsException("Missing access token");

        tokenBlacklistService.blacklist(accessToken);

        String hash = HashUtils.sha256(refreshToken);

        RefreshTokenEntity rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        // FIX-AUTH-003: Verify session ownership - user can only logout their own sessions
        if (callingUserId != null && !rt.getUserId().equals(callingUserId)) {
            throw new InvalidCredentialsException("Cannot revoke another user's session");
        }

        if (rt.isRevoked())
            throw new InvalidCredentialsException("Already revoked");

        tracingService.tagCurrentSpan(rt.getUserId(), rt.getDeviceId(), rt.getIp(), rt.getJti());

        requestTimer.record(() -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            logoutSuccessCounter.increment();
        });

        // Audit logging
        UserEntity user = userRepository.findById(rt.getUserId()).orElse(null);
        String username = user != null ? user.getUsername() : "unknown";
        auditLogService.logLogout(rt.getUserId(), username, rt.getIp(), rt.getDeviceId());
    }
}
