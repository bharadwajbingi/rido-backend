package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRegistrationService userRegistrationService;

    @Mock
    private LoginService loginService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LogoutService logoutService;

    @InjectMocks
    private AuthService authService;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String IP = "192.168.1.1";
    private static final String DEVICE_ID = "device-123";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String REFRESH_TOKEN = "refresh-token-abc";
    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Test
    @DisplayName("register should delegate to UserRegistrationService")
    void shouldRegister_delegatesToUserRegistrationService() {
        UserEntity expectedUser = new UserEntity();
        expectedUser.setUsername(USERNAME);
        when(userRegistrationService.register(USERNAME, PASSWORD, IP)).thenReturn(expectedUser);

        UserEntity result = authService.register(USERNAME, PASSWORD, IP);

        assertThat(result).isEqualTo(expectedUser);
        verify(userRegistrationService).register(USERNAME, PASSWORD, IP);
    }

    @Test
    @DisplayName("login should delegate to LoginService")
    void shouldLogin_delegatesToLoginService() {
        TokenResponse expectedResponse = new TokenResponse(ACCESS_TOKEN, REFRESH_TOKEN, 3600);
        when(loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT)).thenReturn(expectedResponse);

        TokenResponse result = authService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT);

        assertThat(result).isEqualTo(expectedResponse);
        verify(loginService).login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT);
    }

    @Test
    @DisplayName("refresh should delegate to RefreshTokenService")
    void shouldRefresh_delegatesToRefreshTokenService() {
        TokenResponse expectedResponse = new TokenResponse(ACCESS_TOKEN, "new-refresh", 3600);
        when(refreshTokenService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT)).thenReturn(expectedResponse);

        TokenResponse result = authService.refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT);

        assertThat(result).isEqualTo(expectedResponse);
        verify(refreshTokenService).refresh(REFRESH_TOKEN, DEVICE_ID, IP, USER_AGENT);
    }

    @Test
    @DisplayName("logout should delegate to LogoutService")
    void shouldLogout_delegatesToLogoutService() {
        UUID callingUserId = UUID.randomUUID();

        authService.logout(REFRESH_TOKEN, ACCESS_TOKEN, callingUserId);

        verify(logoutService).logout(REFRESH_TOKEN, ACCESS_TOKEN, callingUserId);
    }
}
