package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRegistrationService userRegistrationService;
    @Mock private LoginService loginService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LogoutService logoutService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRegistrationService,
            loginService,
            refreshTokenService,
            logoutService
        );
    }

    @Test
    void register_shouldDelegateToUserRegistrationService() {
        // Arrange
        String username = "testuser";
        String password = "password";
        String ip = "127.0.0.1";
        UserEntity mockUser = new UserEntity();
        when(userRegistrationService.register(username, password, ip)).thenReturn(mockUser);

        // Act
        authService.register(username, password, ip);

        // Assert
        verify(userRegistrationService).register(username, password, ip);
    }

    @Test
    void login_shouldDelegateToLoginService() {
        // Arrange
        String username = "testuser";
        String password = "password";
        String deviceId = "device-1";
        String ip = "127.0.0.1";
        String userAgent = "test-agent";

        TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600L);
        when(loginService.login(username, password, deviceId, ip, userAgent)).thenReturn(expectedResponse);

        // Act
        TokenResponse result = authService.login(username, password, deviceId, ip, userAgent);

        // Assert
        verify(loginService).login(username, password, deviceId, ip, userAgent);
        assert result == expectedResponse;
    }

    @Test
    void refresh_shouldDelegateToRefreshTokenService() {
        // Arrange
        String refreshToken = "refresh-token";
        String deviceId = "device-1";
        String ip = "127.0.0.1";

        TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600L);
        when(refreshTokenService.refresh(refreshToken, deviceId, ip)).thenReturn(expectedResponse);

        // Act
        TokenResponse result = authService.refresh(refreshToken, deviceId, ip);

        // Assert
        verify(refreshTokenService).refresh(refreshToken, deviceId, ip);
        assert result == expectedResponse;
    }

    @Test
    void logout_shouldDelegateToLogoutService() {
        // Arrange
        String refreshToken = "refresh-token";
        String accessToken = "access-token";

        // Act
        authService.logout(refreshToken, accessToken);

        // Assert
        verify(logoutService).logout(refreshToken, accessToken);
    }
}
