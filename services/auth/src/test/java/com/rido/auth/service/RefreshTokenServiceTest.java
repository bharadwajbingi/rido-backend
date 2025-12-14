package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.DeviceMismatchException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.TokenExpiredException;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private TracingService tracingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter refreshSuccessCounter;

    @Mock
    private Counter refreshReplayCounter;

    @Mock
    private Timer requestTimer;

    private RefreshTokenService refreshTokenService;

    private static final String REFRESH_TOKEN = "refresh-token-abc";
    private static final String DEVICE_ID = "device-123";
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("auth.refresh.ok")).thenReturn(refreshSuccessCounter);
        when(meterRegistry.counter("auth.refresh.replay")).thenReturn(refreshReplayCounter);
        when(meterRegistry.timer("auth.request.duration")).thenReturn(requestTimer);

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                tokenService,
                tracingService,
                auditLogService,
                meterRegistry
        );
    }

    private RefreshTokenEntity createRefreshToken(boolean revoked, Instant expiresAt) {
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setId(UUID.randomUUID());
        rt.setUserId(USER_ID);
        rt.setTokenHash("hash");
        rt.setRevoked(revoked);
        rt.setExpiresAt(expiresAt);
        rt.setDeviceId(DEVICE_ID);
        rt.setUserAgent(USER_AGENT);
        rt.setIp(IP);
        rt.setJti(UUID.randomUUID());
        return rt;
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidation {

        @Test
        @DisplayName("Should fail refresh when token not found")
        void shouldFailRefresh_whenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        @DisplayName("Should fail refresh when token is revoked")
        void shouldFailRefresh_whenTokenRevoked() {
            RefreshTokenEntity rt = createRefreshToken(true, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(refreshTokenRepository.getDatabaseTime()).thenReturn(Instant.now());

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Refresh token revoked");
        }

        @Test
        @DisplayName("Should fail refresh when token is expired")
        void shouldFailRefresh_whenTokenExpired() {
            RefreshTokenEntity rt = createRefreshToken(false, Instant.now().minusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(refreshTokenRepository.getDatabaseTime()).thenReturn(Instant.now());

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(TokenExpiredException.class)
                    .hasMessage("Refresh token expired");

            verify(refreshTokenRepository).save(argThat(token -> token.isRevoked()));
        }
    }

    @Nested
    @DisplayName("Device Security Tests")
    class DeviceSecurity {

        @Test
        @DisplayName("Should fail refresh when device ID mismatches")
        void shouldFailRefresh_whenDeviceMismatch() {
            RefreshTokenEntity rt = createRefreshToken(false, Instant.now().plusSeconds(3600));
            UserEntity user = new UserEntity();
            user.setId(USER_ID);
            user.setUsername("testuser");

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, "different-device", IP, USER_AGENT))
                    .isInstanceOf(DeviceMismatchException.class)
                    .hasMessage("Device or User-Agent mismatch detected");

            verify(auditLogService).logDeviceMismatch(
                    eq(USER_ID), eq("testuser"), eq(IP),
                    eq(DEVICE_ID), eq("different-device"),
                    eq(USER_AGENT), eq(USER_AGENT)
            );
        }

        @Test
        @DisplayName("Should fail refresh when user agent mismatches")
        void shouldFailRefresh_whenUserAgentMismatch() {
            RefreshTokenEntity rt = createRefreshToken(false, Instant.now().plusSeconds(3600));
            UserEntity user = new UserEntity();
            user.setId(USER_ID);
            user.setUsername("testuser");

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, "Different/Agent"))
                    .isInstanceOf(DeviceMismatchException.class);
        }
    }

    @Nested
    @DisplayName("User Validation Tests")
    class UserValidation {

        @Test
        @DisplayName("Should fail refresh when user no longer exists")
        void shouldFailRefresh_whenUserNoLongerExists() {
            RefreshTokenEntity rt = createRefreshToken(false, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(refreshTokenRepository.getDatabaseTime()).thenReturn(Instant.now());
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("User no longer exists");
        }
    }

    @Nested
    @DisplayName("Successful Refresh Tests")
    class SuccessfulRefresh {

        @Test
        @DisplayName("Should refresh successfully with valid token")
        void shouldRefresh_whenValidToken() {
            RefreshTokenEntity rt = createRefreshToken(false, Instant.now().plusSeconds(3600));
            UserEntity user = new UserEntity();
            user.setId(USER_ID);
            user.setUsername("testuser");
            user.setRole("USER");
            TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600);

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(refreshTokenRepository.getDatabaseTime()).thenReturn(Instant.now());
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(requestTimer.record(any(Supplier.class))).thenAnswer(inv -> {
                Supplier<?> supplier = inv.getArgument(0);
                return supplier.get();
            });
            when(tokenService.createTokens(USER_ID, "USER", DEVICE_ID, IP, USER_AGENT))
                    .thenReturn(expectedResponse);

            TokenResponse result = refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT);

            assertThat(result).isEqualTo(expectedResponse);
            verify(refreshTokenRepository).save(argThat(token -> token.isRevoked()));
            verify(auditLogService).logRefresh(USER_ID, "testuser", IP, DEVICE_ID);
        }
    }
}
