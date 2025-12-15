package com.rido.auth.integration;

import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for audit logging.
 * 
 * Tests that audit logs are correctly written to Postgres for various events.
 */
@DisplayName("Audit Log Integration Tests")
public class AuditLogIT extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should log successful registration")
    void shouldLogSuccessfulRegistration() {
        createTestUser("audituser", "Password123!");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.SIGNUP &&
                log.getUsername().equals("audituser") &&
                log.isSuccess() &&
                log.getIpAddress() != null
        );
    }

    @Test
    @DisplayName("Should log successful login")
    void shouldLogSuccessfulLogin() {
        createTestUser("loginaudit", "Password123!");
        loginAndGetTokens("loginaudit", "Password123!");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_SUCCESS &&
                log.getUsername().equals("loginaudit") &&
                log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should log failed login with reason")
    void shouldLogFailedLogin() {
        createTestUser("failaudit", "CorrectPassword!");

        // Attempt failed login
        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                new com.rido.auth.dto.LoginRequest("failaudit", "WrongPassword!", null, null, null),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_FAILED &&
                log.getUsername().equals("failaudit") &&
                !log.isSuccess() &&
                log.getFailureReason() != null &&
                log.getFailureReason().contains("Invalid credentials")
        );
    }

    @Test
    @DisplayName("Should log token refresh")
    void shouldLogTokenRefresh() {
        createTestUser("refreshaudit", "Password123!");
        com.rido.auth.dto.TokenResponse tokens = loginAndGetTokens("refreshaudit", "Password123!");

        // Refresh tokens
        com.rido.auth.dto.RefreshRequest request = new com.rido.auth.dto.RefreshRequest(tokens.getRefreshToken());
        org.springframework.http.HttpHeaders headers = headersWithDevice("test-device", "Test-Agent");
        org.springframework.http.HttpEntity<com.rido.auth.dto.RefreshRequest> entity = 
                new org.springframework.http.HttpEntity<>(request, headers);

        restTemplate.postForEntity(baseUrl() + "/refresh", entity, com.rido.auth.dto.TokenResponse.class);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.REFRESH_TOKEN &&
                log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should log device mismatch")
    void shouldLogDeviceMismatch() {
        createTestUser("deviceaudit", "Password123!");
        com.rido.auth.dto.TokenResponse tokens = loginAndGetTokens("deviceaudit", "Password123!", "device-1", "Agent-1");

        // Try to refresh with different device
        com.rido.auth.dto.RefreshRequest request = new com.rido.auth.dto.RefreshRequest(tokens.getRefreshToken());
        org.springframework.http.HttpHeaders headers = headersWithDevice("device-2", "Agent-1");
        org.springframework.http.HttpEntity<com.rido.auth.dto.RefreshRequest> entity = 
                new org.springframework.http.HttpEntity<>(request, headers);

        restTemplate.postForEntity(baseUrl() + "/refresh", entity, String.class);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.DEVICE_MISMATCH &&
                !log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should log session revocation")
    void shouldLogSessionRevocation() {
        createTestUser("revokeaudit", "Password123!");
        com.rido.auth.dto.TokenResponse tokens = loginAndGetTokens("revokeaudit", "Password123!");

        // Revoke all sessions
        org.springframework.http.HttpHeaders headers = headersWithAuth(tokens.getAccessToken());
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

        restTemplate.postForEntity(baseUrl() + "/sessions/revoke-all", entity, String.class);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.SESSION_REVOKED &&
                log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should verify audit log indexes exist")
    void shouldVerifyIndexesExist() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                        "WHERE schemaname = 'auth' AND tablename = 'audit_logs'"
        );

        assertThat(indexes).extracting(idx -> idx.get("indexname"))
                .contains("idx_audit_event_type", "idx_audit_user_id", 
                         "idx_audit_timestamp", "idx_audit_username");
    }

    @Test
    @DisplayName("Should include device info in audit logs")
    void shouldIncludeDeviceInfoInAuditLogs() {
        createTestUser("devicelog", "Password123!");
        loginAndGetTokens("devicelog", "Password123!", "custom-device-123", "Custom-Agent/1.0");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_SUCCESS &&
                log.getDeviceId() != null &&
                log.getUserAgent() != null &&
                log.getDeviceId().equals("custom-device-123") &&
                log.getUserAgent().equals("Custom-Agent/1.0")
        );
    }

    @Test
    @DisplayName("Should set timestamp on audit logs")
    void shouldSetTimestampOnAuditLogs() {
        createTestUser("timestampaudit", "Password123!");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).allMatch(log -> log.getTimestamp() != null);
    }

    @Test
    @DisplayName("Should include user ID where applicable")
    void shouldIncludeUserId() {
        com.rido.auth.model.UserEntity user = createTestUser("useridaudit", "Password123!");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.SIGNUP &&
                log.getUserId() != null &&
                log.getUserId().equals(user.getId())
        );
    }
}
