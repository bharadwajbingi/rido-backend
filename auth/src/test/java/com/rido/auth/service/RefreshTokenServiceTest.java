package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.DeviceMismatchException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import com.rido.auth.util.HashUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;
    @Mock private TracingService tracingService;
    @Mock private AuditLogService auditLogService;
    @Mock private MeterRegistry registry;
    @Mock private Counter counter;
    @Mock private Timer timer;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        lenient().when(registry.counter(anyString())).thenReturn(counter);
        lenient().when(registry.timer(anyString())).thenReturn(timer);
        lenient().when(timer.record(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier) inv.getArgument(0)).get());

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                tokenService,
                tracingService,
                auditLogService,
                registry
        );
    }

    @Test
    void refresh_shouldSucceed_whenDeviceAndUserAgentMatch() {
        // Arrange
        String refreshToken = "valid-token";
        String deviceId = "device-1";
        String ip = "127.0.0.1";
        String userAgent = "test-agent";
        String hash = HashUtils.sha256(refreshToken);

        UUID userId = UUID.randomUUID();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setDeviceId(deviceId);
        rt.setUserAgent(userAgent);
        rt.setExpiresAt(Instant.now().plusSeconds(3600));
        rt.setRevoked(false);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");
        user.setRole("USER");

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.getDatabaseTime()).thenReturn(Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenService.createTokens(any(), any(), any(), any(), any()))
                .thenReturn(new TokenResponse("new-access", "new-refresh", 3600L));

        // Act
        TokenResponse result = refreshTokenService.refresh(refreshToken, deviceId, ip, userAgent);

        // Assert
        assertNotNull(result);
        verify(auditLogService).logRefresh(userId, "testuser", ip, deviceId);
        verify(auditLogService, never()).logDeviceMismatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refresh_shouldThrowDeviceMismatch_whenDeviceIdChanged() {
        // Arrange
        String refreshToken = "valid-token";
        String storedDeviceId = "device-1";
        String newDeviceId = "device-2";
        String ip = "127.0.0.1";
        String userAgent = "test-agent";
        String hash = HashUtils.sha256(refreshToken);

        UUID userId = UUID.randomUUID();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setDeviceId(storedDeviceId);
        rt.setUserAgent(userAgent);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(rt));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(DeviceMismatchException.class, () -> 
            refreshTokenService.refresh(refreshToken, newDeviceId, ip, userAgent)
        );

        verify(auditLogService).logDeviceMismatch(
                eq(userId), 
                eq("testuser"), 
                eq(ip), 
                eq(storedDeviceId), 
                eq(newDeviceId), 
                eq(userAgent), 
                eq(userAgent)
        );
    }

    @Test
    void refresh_shouldThrowDeviceMismatch_whenUserAgentChanged() {
        // Arrange
        String refreshToken = "valid-token";
        String deviceId = "device-1";
        String ip = "127.0.0.1";
        String storedUserAgent = "agent-1";
        String newUserAgent = "agent-2";
        String hash = HashUtils.sha256(refreshToken);

        UUID userId = UUID.randomUUID();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setDeviceId(deviceId);
        rt.setUserAgent(storedUserAgent);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("testuser");

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(rt));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(DeviceMismatchException.class, () -> 
            refreshTokenService.refresh(refreshToken, deviceId, ip, newUserAgent)
        );

        verify(auditLogService).logDeviceMismatch(
                eq(userId), 
                eq("testuser"), 
                eq(ip), 
                eq(deviceId), 
                eq(deviceId), 
                eq(storedUserAgent), 
                eq(newUserAgent)
        );
    }
}
