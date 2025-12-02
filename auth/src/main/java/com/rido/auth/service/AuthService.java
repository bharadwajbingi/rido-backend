package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import org.springframework.stereotype.Service;

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

    public void register(String username, String password) {
        userRegistrationService.register(username, password);
    }

    public TokenResponse login(String username, String password, String deviceId, String ip, String userAgent) {
        return loginService.login(username, password, deviceId, ip, userAgent);
    }

    public TokenResponse refresh(String refreshToken, String deviceId, String ip) {
        return refreshTokenService.refresh(refreshToken, deviceId, ip);
    }

    public void logout(String refreshToken, String accessToken) {
        logoutService.logout(refreshToken, accessToken);
    }
}
