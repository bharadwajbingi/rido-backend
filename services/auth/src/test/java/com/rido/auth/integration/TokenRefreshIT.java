package com.rido.auth.integration;

import com.rido.auth.dto.RefreshRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for token refresh flow.
 * 
 * Tests token refresh rotation, validation, and security checks.
 */
@DisplayName("Token Refresh Integration Tests")
public class TokenRefreshIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should refresh tokens successfully")
    void shouldRefreshTokensSuccessfully() {
        createTestUser("refreshuser", "Password123!");
        TokenResponse initialTokens = loginAndGetTokens("refreshuser", "Password123!");

        String initialRefreshToken = initialTokens.refreshToken();

        // Refresh tokens
        RefreshRequest request = new RefreshRequest(initialRefreshToken);
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse newTokens = response.getBody();
        assertThat(newTokens).isNotNull();
        assertThat(newTokens.accessToken()).isNotBlank();
        assertThat(newTokens.accessToken()).isNotEqualTo(initialTokens.accessToken());
        assertThat(newTokens.refreshToken()).isNotBlank();
        assertThat(newTokens.refreshToken()).isNotEqualTo(initialRefreshToken);

        // Verify old refresh token is revoked
        String oldTokenHash = HashUtils.sha256(initialRefreshToken);
        RefreshTokenEntity oldToken = refreshTokenRepository.findByTokenHash(oldTokenHash).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();

        // Verify new refresh token exists
        String newTokenHash = HashUtils.sha256(newTokens.refreshToken());
        RefreshTokenEntity newToken = refreshTokenRepository.findByTokenHash(newTokenHash).orElseThrow();
        assertThat(newToken.isRevoked()).isFalse();

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.TOKEN_REFRESH &&
                log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should reject refresh with invalid token")
    void shouldRejectInvalidRefreshToken() {
        RefreshRequest request = new RefreshRequest("invalid-token-12345");
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject refresh with revoked token")
    void shouldRejectRevokedRefreshToken() {
        createTestUser("revoketest", "Password123!");
        TokenResponse tokens = loginAndGetTokens("revoketest", "Password123!");

        // Revoke the refresh token
        String tokenHash = HashUtils.sha256(tokens.refreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Try to refresh with revoked token
        RefreshRequest request = new RefreshRequest(tokens.refreshToken());
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject refresh with expired token")
    void shouldRejectExpiredRefreshToken() {
        createTestUser("expiretest", "Password123!");
        TokenResponse tokens = loginAndGetTokens("expiretest", "Password123!");

        // Manually expire the token
        String tokenHash = HashUtils.sha256(tokens.refreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(300)); // Expired 5 minutes ago
        refreshTokenRepository.save(token);

        // Try to refresh with expired token
        RefreshRequest request = new RefreshRequest(tokens.refreshToken());
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify token is now revoked to prevent replay
        RefreshTokenEntity revokedToken = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(revokedToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should reject refresh with device ID mismatch")
    void shouldRejectDeviceIdMismatch() {
        createTestUser("devicetest", "Password123!");
        TokenResponse tokens = loginAndGetTokens("devicetest", "Password123!", "device-1", "Agent-1");

        // Try to refresh with different device ID
        RefreshRequest request = new RefreshRequest(tokens.refreshToken());
        HttpHeaders headers = headersWithDevice("device-2", "Agent-1");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Device");

        // Verify audit log for device mismatch
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.DEVICE_MISMATCH &&
                !log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should reject refresh with user agent mismatch")
    void shouldRejectUserAgentMismatch() {
        createTestUser("agenttest", "Password123!");
        TokenResponse tokens = loginAndGetTokens("agenttest", "Password123!", "device-1", "Agent-1");

        // Try to refresh with different user agent
        RefreshRequest request = new RefreshRequest(tokens.refreshToken());
        HttpHeaders headers = headersWithDevice("device-1", "Agent-2");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.DEVICE_MISMATCH
        );
    }

    @Test
    @DisplayName("Should enforce rate limit on refresh (20 per minute)")
    void shouldEnforceRateLimitOnRefresh() {
        createTestUser("refreshrate", "Password123!");

        // Create 25 tokens and try to refresh all
        for (int i = 0; i < 25; i++) {
            TokenResponse tokens = loginAndGetTokens("refreshrate", "Password123!");
            
            if (i < 20) {
                // First 20 should succeed
                RefreshRequest request = new RefreshRequest(tokens.refreshToken());
                HttpHeaders headers = headersWithDevice("device-" + i, "Agent");
                HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

                ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                        baseUrl() + "/refresh",
                        entity,
                        TokenResponse.class
                );
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            } else {
                // 21st should be rate limited
                RefreshRequest request = new RefreshRequest(tokens.refreshToken());
                HttpHeaders headers = headersWithDevice("device-" + i, "Agent");
                HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl() + "/refresh",
                        entity,
                        String.class
                );
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                break; // Stop after first rate limit hit
            }
        }

        // Verify Redis rate limit key
        String rateLimitKey = "rate:refresh:127.0.0.1";
        Boolean keyExists = redisTemplate.hasKey(rateLimitKey);
        assertThat(keyExists).isTrue();
    }

    @Test
    @DisplayName("Should not allow reuse of old refresh token after rotation")
    void shouldNotAllowRefreshTokenReuse() {
        createTestUser("reusetest", "Password123!");
        TokenResponse tokens = loginAndGetTokens("reusetest", "Password123!");

        String refreshToken = tokens.refreshToken();

        // Use token once
        RefreshRequest request = new RefreshRequest(refreshToken);
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TokenResponse> firstResponse = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                TokenResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Try to use same token again
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject refresh if user no longer exists")
    void shouldRejectRefreshIfUserDeleted() {
        UserEntity user = createTestUser("deleteuser", "Password123!");
        TokenResponse tokens = loginAndGetTokens("deleteuser", "Password123!");

        // Delete the user
        userRepository.delete(user);

        // Try to refresh
        RefreshRequest request = new RefreshRequest(tokens.refreshToken());
        HttpHeaders headers = headersWithDevice("test-device-id", "Test-User-Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
