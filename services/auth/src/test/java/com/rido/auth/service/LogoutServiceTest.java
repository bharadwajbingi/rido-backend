package com.rido.auth.service;

import com.rido.auth.exception.InvalidCredentialsException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TracingService tracingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter logoutSuccessCounter;

    @Mock
    private Timer requestTimer;

    private LogoutService logoutService;

    private static final String REFRESH_TOKEN = "refresh-token-abc";
    private static final String ACCESS_TOKEN = "access-token-xyz";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String DEVICE_ID = "device-123";
    private static final String IP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("auth.logout.ok")).thenReturn(logoutSuccessCounter);
        when(meterRegistry.timer("auth.request.duration")).thenReturn(requestTimer);

        logoutService = new LogoutService(
                refreshTokenRepository,
                tokenBlacklistService,
                tracingService,
                auditLogService,
                userRepository,
                meterRegistry
        );
    }

    private RefreshTokenEntity createRefreshToken(UUID userId, boolean revoked) {
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setRevoked(revoked);
        rt.setDeviceId(DEVICE_ID);
        rt.setIp(IP);
        rt.setJti(UUID.randomUUID());
        return rt;
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidation {

        @Test
        @DisplayName("Should fail logout when refresh token is null")
        void shouldFailLogout_whenRefreshTokenNull() {
            assertThatThrownBy(() -> logoutService.logout(null, ACCESS_TOKEN, USER_ID))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Missing refresh token");
        }

        @Test
        @DisplayName("Should fail logout when access token is null")
        void shouldFailLogout_whenAccessTokenNull() {
            assertThatThrownBy(() -> logoutService.logout(REFRESH_TOKEN, null, USER_ID))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Missing access token");
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidation {

        @Test
        @DisplayName("Should fail logout when refresh token not found")
        void shouldFailLogout_whenRefreshTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> logoutService.logout(REFRESH_TOKEN, ACCESS_TOKEN, USER_ID))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid refresh token");

            verify(tokenBlacklistService).blacklist(ACCESS_TOKEN);
        }

        @Test
        @DisplayName("Should fail logout when refresh token is already revoked")
        void shouldFailLogout_whenRefreshTokenAlreadyRevoked() {
            RefreshTokenEntity rt = createRefreshToken(USER_ID, true);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

            assertThatThrownBy(() -> logoutService.logout(REFRESH_TOKEN, ACCESS_TOKEN, USER_ID))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Already revoked");
        }
    }

    @Nested
    @DisplayName("Session Ownership Tests")
    class SessionOwnership {

        @Test
        @DisplayName("Should fail logout when user ID does not match session owner")
        void shouldFailLogout_whenUserIdMismatch() {
            UUID differentUserId = UUID.randomUUID();
            RefreshTokenEntity rt = createRefreshToken(USER_ID, false);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

            assertThatThrownBy(() -> logoutService.logout(REFRESH_TOKEN, ACCESS_TOKEN, differentUserId))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Cannot revoke another user's session");
        }
    }

    @Nested
    @DisplayName("Successful Logout Tests")
    class SuccessfulLogout {

        @Test
        @DisplayName("Should logout successfully with valid tokens")
        void shouldLogout_whenValidTokens() {
            RefreshTokenEntity rt = createRefreshToken(USER_ID, false);
            UserEntity user = new UserEntity();
            user.setId(USER_ID);
            user.setUsername("testuser");

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            doAnswer(inv -> {
                Runnable runnable = inv.getArgument(0);
                runnable.run();
                return null;
            }).when(requestTimer).record(any(Runnable.class));

            logoutService.logout(REFRESH_TOKEN, ACCESS_TOKEN, USER_ID);

            verify(tokenBlacklistService).blacklist(ACCESS_TOKEN);
            verify(refreshTokenRepository).save(argThat(token -> token.isRevoked()));
            verify(auditLogService).logLogout(USER_ID, "testuser", IP, DEVICE_ID);
        }

        @Test
        @DisplayName("Should blacklist access token on logout")
        void shouldBlacklistAccessToken_onLogout() {
            RefreshTokenEntity rt = createRefreshToken(USER_ID, false);
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
            doAnswer(inv -> {
                Runnable runnable = inv.getArgument(0);
                runnable.run();
                return null;
            }).when(requestTimer).record(any(Runnable.class));

            logoutService.logout(REFRESH_TOKEN, ACCESS_TOKEN, USER_ID);

            verify(tokenBlacklistService).blacklist(ACCESS_TOKEN);
        }
    }
}
