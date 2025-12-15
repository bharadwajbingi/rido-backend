package com.rido.auth.integration;

import com.rido.auth.dto.SessionDTO;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for session management.
 * 
 * Tests multi-session handling, listing, revocation, and limits.
 */
@DisplayName("Session Management Integration Tests")
public class SessionManagementIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should create multiple sessions for same user")
    void shouldCreateMultipleSessions() {
        createTestUser("multisession", "Password123!");

        // Login from 3 different devices
        TokenResponse session1 = loginAndGetTokens("multisession", "Password123!", "device-1", "Agent-1");
        TokenResponse session2 = loginAndGetTokens("multisession", "Password123!", "device-2", "Agent-2");
        TokenResponse session3 = loginAndGetTokens("multisession", "Password123!", "device-3", "Agent-3");

        assertThat(session1.refreshToken()).isNotEqualTo(session2.refreshToken());
        assertThat(session2.refreshToken()).isNotEqualTo(session3.refreshToken());

        // Verify all sessions exist in database
        UserEntity user = userRepository.findByUsername("multisession").orElseThrow();
        List<RefreshTokenEntity> sessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(sessions).hasSize(3);
    }

    @Test
    @DisplayName("Should list all active sessions for user")
    void shouldListActiveSessions() {
        createTestUser("listsessions", "Password123!");

        // Create 3 sessions
        TokenResponse token1 = loginAndGetTokens("listsessions", "Password123!", "device-1", "Agent-1");
        loginAndGetTokens("listsessions", "Password123!", "device-2", "Agent-2");
        loginAndGetTokens("listsessions", "Password123!", "device-3", "Agent-3");

        // List sessions
        HttpHeaders headers = headersWithAuth(token1.accessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<SessionDTO>> response = restTemplate.exchange(
                baseUrl() + "/sessions",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<SessionDTO>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SessionDTO> sessions = response.getBody();
        assertThat(sessions).isNotNull();
        assertThat(sessions).hasSize(3);

        // Verify session details
        assertThat(sessions).extracting(SessionDTO::deviceId)
                .containsExactlyInAnyOrder("device-1", "device-2", "device-3");
        assertThat(sessions).extracting(SessionDTO::revoked)
                .containsOnly(false);
    }

    @Test
    @DisplayName("Should enforce max active sessions limit (5)")
    void shouldEnforceMaxSessionsLimit() {
        createTestUser("maxsessions", "Password123!");

        // Create 6 sessions (limit is 5)
        for (int i = 1; i <= 6; i++) {
            loginAndGetTokens("maxsessions", "Password123!", "device-" + i, "Agent-" + i);
        }

        // Verify only 5 active sessions exist
        UserEntity user = userRepository.findByUsername("maxsessions").orElseThrow();
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeSessions).hasSize(5);

        // Verify total sessions created is 6 (oldest is revoked)
        List<RefreshTokenEntity> allSessions = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .toList();
        assertThat(allSessions).hasSize(6);

        // Verify oldest session is revoked
        RefreshTokenEntity oldestSession = allSessions.stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElseThrow();
        assertThat(oldestSession.isRevoked()).isTrue();

        // Verify audit log for auto-revocation
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.SESSION_REVOKED &&
                log.getFailureReason() != null &&
                log.getFailureReason().contains("SESSION_LIMIT_EXCEEDED")
        );
    }

    @Test
    @DisplayName("Should revoke all sessions for user")
    void shouldRevokeAllSessions() {
        createTestUser("revokeall", "Password123!");

        // Create 3 sessions
        TokenResponse activeToken = loginAndGetTokens("revokeall", "Password123!", "device-1", "Agent-1");
        loginAndGetTokens("revokeall", "Password123!", "device-2", "Agent-2");
        loginAndGetTokens("revokeall", "Password123!", "device-3", "Agent-3");

        // Revoke all sessions
        HttpHeaders headers = headersWithAuth(activeToken.accessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/sessions/revoke-all",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify all sessions are revoked
        UserEntity user = userRepository.findByUsername("revokeall").orElseThrow();
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeSessions).isEmpty();

        List<RefreshTokenEntity> allSessions = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .toList();
        assertThat(allSessions).hasSize(3);
        assertThat(allSessions).allMatch(RefreshTokenEntity::isRevoked);
    }

    @Test
    @DisplayName("Should revoke single session by ID")
    void shouldRevokeSingleSession() {
        createTestUser("revokeone", "Password123!");

        // Create 3 sessions
        TokenResponse token1 = loginAndGetTokens("revokeone", "Password123!", "device-1", "Agent-1");
        loginAndGetTokens("revokeone", "Password123!", "device-2", "Agent-2");
        loginAndGetTokens("revokeone", "Password123!", "device-3", "Agent-3");

        // Get session IDs
        HttpHeaders headers = headersWithAuth(token1.accessToken());
        HttpEntity<Void> listEntity = new HttpEntity<>(headers);

        ResponseEntity<List<SessionDTO>> listResponse = restTemplate.exchange(
                baseUrl() + "/sessions",
                HttpMethod.GET,
                listEntity,
                new ParameterizedTypeReference<List<SessionDTO>>() {}
        );

        List<SessionDTO> sessions = listResponse.getBody();
        assertThat(sessions).isNotNull();
        UUID sessionToRevoke = sessions.stream()
                .filter(s -> "device-2".equals(s.deviceId()))
                .findFirst()
                .orElseThrow()
                .id();

        // Revoke one session
        HttpEntity<Void> revokeEntity = new HttpEntity<>(headers);
        ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
                baseUrl() + "/sessions/" + sessionToRevoke + "/revoke",
                revokeEntity,
                String.class
        );

        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify only that session is revoked
        RefreshTokenEntity revokedSession = refreshTokenRepository.findById(sessionToRevoke).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();

        // Verify other sessions still active
        UserEntity user = userRepository.findByUsername("revokeone").orElseThrow();
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeSessions).hasSize(2);
    }

    @Test
    @DisplayName("Should not allow revoking another user's session")
    void shouldNotAllowRevokingOtherUserSession() {
        createTestUser("user1", "Password123!");
        createTestUser("user2", "Password456!");

        // User1 creates session
        TokenResponse user1Token = loginAndGetTokens("user1", "Password123!");
        
        // User2 creates session
        TokenResponse user2Token = loginAndGetTokens("user2", "Password456!");

        // Get user2's session ID
        UserEntity user2 = userRepository.findByUsername("user2").orElseThrow();
        List<RefreshTokenEntity> user2Sessions = refreshTokenRepository.findActiveByUserId(user2.getId());
        UUID user2SessionId = user2Sessions.get(0).getId();

        // User1 tries to revoke user2's session
        HttpHeaders headers = headersWithAuth(user1Token.accessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/sessions/" + user2SessionId + "/revoke",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify user2's session is still active
        RefreshTokenEntity user2Session = refreshTokenRepository.findById(user2SessionId).orElseThrow();
        assertThat(user2Session.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated session list request")
    void shouldRejectUnauthenticatedSessionList() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/sessions",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create audit logs for session revocations")
    void shouldCreateAuditLogsForRevocations() {
        createTestUser("auditrevoke", "Password123!");
        TokenResponse token = loginAndGetTokens("auditrevoke", "Password123!");

        // Revoke all sessions
        HttpHeaders headers = headersWithAuth(token.accessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.postForEntity(
                baseUrl() + "/sessions/revoke-all",
                entity,
                String.class
        );

        // Verify audit logs exist
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.SESSION_REVOKED &&
                log.getUsername().equals("auditrevoke")
        );
    }
}
