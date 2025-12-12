package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.UserEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRegistrationService userRegistrationService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;

    public AuthService(
            UserRegistrationService userRegistrationService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            LogoutService logoutService
    ) {
        this.userRegistrationService = userRegistrationService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
    }

    public UserEntity register(String username, String password, String ip) {
        return userRegistrationService.register(username, password, ip);
    }

    public TokenResponse login(String username, String password, String deviceId, String ip, String userAgent) {
        return loginService.login(username, password, deviceId, ip, userAgent);
    }

    public TokenResponse refresh(String refreshToken, String deviceId, String ip, String userAgent) {
        return refreshTokenService.refresh(refreshToken, deviceId, ip, userAgent);
    }

    /**
     * Logout user by revoking their refresh token.
     * FIX-AUTH-003: Added callingUserId to verify session ownership.
     */
    public void logout(String refreshToken, String accessToken, UUID callingUserId) {
        logoutService.logout(refreshToken, accessToken, callingUserId);
    }
}
