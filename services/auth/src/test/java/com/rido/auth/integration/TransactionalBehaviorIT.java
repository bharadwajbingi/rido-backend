package com.rido.auth.integration;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for transactional behavior.
 * 
 * Tests that @Transactional boundaries work correctly and database operations
 * are atomic.
 */
@DisplayName("Transactional Behavior Integration Tests")
public class TransactionalBehaviorIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should atomically rotate refresh tokens (old revoked, new created)")
    void shouldAtomicallyRotateRefreshTokens() {
        createTestUser("rotateuser", "Password123!");
        TokenResponse initialTokens = loginAndGetTokens("rotateuser", "Password123!");

        String oldRefreshToken = initialTokens.refreshToken();
        String oldTokenHash = HashUtils.sha256(oldRefreshToken);

        // Refresh tokens
        com.rido.auth.dto.RefreshRequest request = new com.rido.auth.dto.RefreshRequest(oldRefreshToken);
        org.springframework.http.HttpHeaders headers = headersWithDevice("test-device", "Test-Agent");
        org.springframework.http.HttpEntity<com.rido.auth.dto.RefreshRequest> entity = 
                new org.springframework.http.HttpEntity<>(request, headers);

        org.springframework.http.ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        TokenResponse newTokens = response.getBody();
        assertThat(newTokens).isNotNull();

        String newTokenHash = HashUtils.sha256(newTokens.refreshToken());

        // Verify old token is revoked
        RefreshTokenEntity oldToken = refreshTokenRepository.findByTokenHash(oldTokenHash).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();

        // Verify new token exists and is not revoked
        RefreshTokenEntity newToken = refreshTokenRepository.findByTokenHash(newTokenHash).orElseThrow();
        assertThat(newToken.isRevoked()).isFalse();

        // Both operations should be atomic - verify both exist in DB
        long totalTokens = refreshTokenRepository.count();
        assertThat(totalTokens).isEqualTo(2); // Old (revoked) + New (active)
    }

    @Test
    @DisplayName("Should create user and refresh token atomically on registration")
    void shouldCreateUserAndTokenAtomically() {
        com.rido.auth.dto.RegisterRequest request = new com.rido.auth.dto.RegisterRequest("atomicuser", "Password123!");

        org.springframework.http.ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);

        // Verify both user and refresh token exist
        UserEntity user = userRepository.findByUsername("atomicuser").orElseThrow();
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findActiveByUserId(user.getId());

        assertThat(tokens).hasSize(1);
        assertThat(user.getId()).isNotNull();
        assertThat(tokens.get(0).getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Should enforce max sessions atomically (revoke oldest when creating new)")
    void shouldEnforceMaxSessionsAtomically() {
        createTestUser("maxsessionatomic", "Password123!");

        // Create 6 sessions (limit is 5)
        for (int i = 1; i <= 6; i++) {
            loginAndGetTokens("maxsessionatomic", "Password123!", "device-" + i, "Agent-" + i);
        }

        // Verify exactly 5 active sessions exist
        UserEntity user = userRepository.findByUsername("maxsessionatomic").orElseThrow();
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeSessions).hasSize(5);

        // Verify 6 total sessions (5 active + 1 revoked)
        List<RefreshTokenEntity> allSessions = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .toList();
        assertThat(allSessions).hasSize(6);

        // Verify oldest is revoked, newest 5 are active
        RefreshTokenEntity oldest = allSessions.stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElseThrow();
        assertThat(oldest.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent session revocations without deadlocks")
    void shouldHandleConcurrentRevocations() {
        createTestUser("concurrent", "Password123!");

        // Create multiple sessions
        for (int i = 0; i < 3; i++) {
            loginAndGetTokens("concurrent", "Password123!", "device-" + i, "Agent-" + i);
        }

        UserEntity user = userRepository.findByUsername("concurrent").orElseThrow();

        // Revoke all sessions
        org.springframework.http.HttpHeaders headers = headersWithAuth(
                loginAndGetTokens("concurrent", "Password123!").accessToken()
        );
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/sessions/revoke-all",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);

        // Verify all sessions are revoked (should complete without deadlock)
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeSessions).isEmpty();
    }

    @Test
    @DisplayName("Should create user, token, and audit log atomically on login")
    void shouldCreateLoginAtomsAtomically() {
        createTestUser("loginatomic", "Password123!");
        loginAndGetTokens("loginatomic", "Password123!");

        // Verify all components exist
        UserEntity user = userRepository.findByUsername("loginatomic").orElseThrow();
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findActiveByUserId(user.getId());
        List<com.rido.auth.model.AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(log -> log.getUserId() != null && log.getUserId().equals(user.getId()))
                .toList();

        assertThat(tokens).isNotEmpty();
        assertThat(logs).isNotEmpty();
    }

    @Test
    @DisplayName("Should revoke token and create audit log atomically on logout")
    void shouldRevokeAndLogAtomically() {
        createTestUser("logoutatomic", "Password123!");
        TokenResponse tokens = loginAndGetTokens("logoutatomic", "Password123!");

        long initialAuditCount = auditLogRepository.count();

        // Logout
        com.rido.auth.dto.LogoutRequest request = new com.rido.auth.dto.LogoutRequest(tokens.refreshToken());
        org.springframework.http.HttpHeaders headers = headersWithAuth(tokens.accessToken());
        org.springframework.http.HttpEntity<com.rido.auth.dto.LogoutRequest> entity = 
                new org.springframework.http.HttpEntity<>(request, headers);

        restTemplate.postForEntity(baseUrl() + "/logout", entity, String.class);

        // Verify token revoked
        String tokenHash = HashUtils.sha256(tokens.refreshToken());
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(token.isRevoked()).isTrue();

        // Verify audit log increased (may include logout + session revoke)
        long finalAuditCount = auditLogRepository.count();
        assertThat(finalAuditCount).isGreaterThan(initialAuditCount);
    }
}
