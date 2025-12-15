package com.rido.auth.integration;

import com.rido.auth.dto.LogoutRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.service.TokenBlacklistService;
import com.rido.auth.util.HashUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for logout flow and token blacklisting.
 * 
 * Tests logout, token revocation, and Redis blacklist functionality.
 */
@DisplayName("Logout Integration Tests")
public class LogoutIT extends BaseIntegrationTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("Should logout successfully and revoke refresh token")
    void shouldLogout() {
        createTestUser("logoutuser", "Password123!");
        TokenResponse tokens = loginAndGetTokens("logoutuser", "Password123!");

        // Logout
        LogoutRequest request = new LogoutRequest(tokens.getRefreshToken());
        HttpHeaders headers = headersWithAuth(tokens.getAccessToken());
        HttpEntity<LogoutRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify refresh token is revoked in database
        String tokenHash = HashUtils.sha256(tokens.getRefreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should blacklist access token on logout")
    void shouldBlacklistAccessToken() {
        createTestUser("blacklistuser", "Password123!");
        TokenResponse tokens = loginAndGetTokens("blacklistuser", "Password123!");

        String accessToken = tokens.getAccessToken();

        // Logout
        LogoutRequest request = new LogoutRequest(tokens.getRefreshToken());
        HttpHeaders headers = headersWithAuth(accessToken);
        HttpEntity<LogoutRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Extract JTI from access token
        String jti = extractJtiFromToken(accessToken);

        // Verify token is blacklisted via TokenBlacklistService
        boolean isBlacklisted = tokenBlacklistService.isBlacklisted(jti);
        assertThat(isBlacklisted).isTrue();

        // Verify Redis key exists
        String blacklistKey = "auth:jti:blacklist:" + jti;
        Boolean keyExists = redisTemplate.hasKey(blacklistKey);
        assertThat(keyExists).isTrue();

        // Verify TTL is set on blacklist key
        Long ttl = redisTemplate.getExpire(blacklistKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(60); // Should be <= access token TTL
    }

    @Test
    @DisplayName("Should reject logout with invalid refresh token")
    void shouldRejectLogoutWithInvalidToken() {
        createTestUser("invalidlogout", "Password123!");
        TokenResponse tokens = loginAndGetTokens("invalidlogout", "Password123!");

        // Try to logout with invalid refresh token
        LogoutRequest request = new LogoutRequest("invalid-refresh-token");
        HttpHeaders headers = headersWithAuth(tokens.getAccessToken());
        HttpEntity<LogoutRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject logout if session ownership mismatch")
    void shouldRejectLogoutWithSessionOwnershipMismatch() {
        // Create two users
        createTestUser("user1", "Password123!");
        createTestUser("user2", "Password456!");

        // Login as both
        TokenResponse user1Tokens = loginAndGetTokens("user1", "Password123!");
        TokenResponse user2Tokens = loginAndGetTokens("user2", "Password456!");

        // Try to logout user2's session using user1's access token
        LogoutRequest request = new LogoutRequest(user2Tokens.getRefreshToken());
        HttpHeaders headers = headersWithAuth(user1Tokens.getAccessToken());
        HttpEntity<LogoutRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify user2's token is NOT revoked
        String tokenHash = HashUtils.sha256(user2Tokens.getRefreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Should handle logout without access token header")
    void shouldHandleLogoutWithoutAccessToken() {
        createTestUser("noheaderlogout", "Password123!");
        TokenResponse tokens = loginAndGetTokens("noheaderlogout", "Password123!");

        // Logout without Authorization header
        LogoutRequest request = new LogoutRequest(tokens.getRefreshToken());
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/logout",
                request,
                String.class
        );

        // Should still work - only refresh token is required
        // Access token blacklisting is optional
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify refresh token is revoked
        String tokenHash = HashUtils.sha256(tokens.getRefreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should set correct TTL on blacklisted token matching expiry")
    void shouldSetCorrectTtlOnBlacklist() {
        createTestUser("ttluser", "Password123!");
        TokenResponse tokens = loginAndGetTokens("ttluser", "Password123!");

        // Logout
        LogoutRequest request = new LogoutRequest(tokens.getRefreshToken());
        HttpHeaders headers = headersWithAuth(tokens.getAccessToken());
        HttpEntity<LogoutRequest> entity = new HttpEntity<>(request, headers);

        restTemplate.postForEntity(baseUrl() + "/logout", entity, String.class);

        // Check TTL
        String jti = extractJtiFromToken(tokens.getAccessToken());
        String blacklistKey = "auth:jti:blacklist:" + jti;
        Long ttl = redisTemplate.getExpire(blacklistKey, TimeUnit.SECONDS);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        // TTL should be close to token expiry (60 seconds in test profile)
        assertThat(ttl).isLessThanOrEqualTo(60L);
    }

    /**
     * Helper method to extract JTI from JWT token
     */
    private String extractJtiFromToken(String token) {
        // Parse token manually to avoid signature verification issues
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        
        // Simple extraction of jti from JSON payload
        // Format: "jti":"UUID-HERE"
        int jtiIndex = payload.indexOf("\"jti\":\"");
        if (jtiIndex == -1) {
            throw new IllegalArgumentException("No JTI in token");
        }
        
        int startIndex = jtiIndex + 7; // Length of "jti":"
        int endIndex = payload.indexOf("\"", startIndex);
        
        return payload.substring(startIndex, endIndex);
    }
}
