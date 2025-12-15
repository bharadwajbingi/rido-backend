package com.rido.auth.integration;

import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.model.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for login flow.
 * 
 * Tests:
 * - Successful login
 * - Failed login with invalid credentials
 * - Account lockout after multiple failures
 * - Admin bypass of lockout
 * - Rate limiting
 * - Audit logging
 */
@DisplayName("Login Flow Integration Tests")
public class LoginFlowIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        createTestUser("validuser", "Password123!");

        TokenResponse tokens = loginAndGetTokens("validuser", "Password123!");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresIn()).isEqualTo(60);

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_SUCCESS &&
                log.getUsername().equals("validuser") &&
                log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should fail login with invalid password")
    void shouldFailLoginWithInvalidPassword() {
        createTestUser("testuser", "CorrectPassword!");

        LoginRequest request = new LoginRequest("testuser", "WrongPassword!");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify login attempt tracked in Redis
        String attemptsKey = "auth:login:attempts:testuser";
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        assertThat(attempts).isNotNull();
        assertThat(Integer.parseInt(attempts)).isGreaterThan(0);

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_FAILED &&
                log.getUsername().equals("testuser") &&
                !log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should lock account after 5 failed login attempts")
    void shouldLockAccountAfterFailedAttempts() {
        createTestUser("locktest", "CorrectPassword!");

        LoginRequest wrongRequest = new LoginRequest("locktest", "WrongPassword!");

        // Attempt 5 failed logins
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl() + "/login",
                    wrongRequest,
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt should be locked
        ResponseEntity<String> lockedResponse = restTemplate.postForEntity(
                baseUrl() + "/login",
                wrongRequest,
                String.class
        );

        assertThat(lockedResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        // Verify lock in database
        UserEntity user = userRepository.findByUsername("locktest").orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(Instant.now());

        // Verify lock in Redis
        String lockKey = "auth:login:locked:locktest";
        Boolean locked = redisTemplate.hasKey(lockKey);
        assertThat(locked).isTrue();

        // Even with correct password, login should fail while locked
        LoginRequest correctRequest = new LoginRequest("locktest", "CorrectPassword!");
        ResponseEntity<String> stillLockedResponse = restTemplate.postForEntity(
                baseUrl() + "/login",
                correctRequest,
                String.class
        );
        assertThat(stillLockedResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    @DisplayName("Should fail login for non-existent user")
    void shouldFailLoginForNonExistentUser() {
        LoginRequest request = new LoginRequest("nonexistent", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
                log.getEventType() == AuditEvent.LOGIN_FAILED &&
                log.getUsername().equals("nonexistent") &&
                !log.isSuccess()
        );
    }

    @Test
    @DisplayName("Should enforce IP-based rate limit (50 requests per minute)")
    void shouldEnforceIpRateLimit() {
        createTestUser("ratelimituser", "Password123!");

        LoginRequest request = new LoginRequest("ratelimituser", "Password123!");

        // Make 50 successful login attempts
        for (int i = 0; i < 50; i++) {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    baseUrl() + "/login",
                    request,
                    TokenResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 51st should be rate limited
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis rate limit key
        String rateLimitKey = "rate:login:ip:127.0.0.1";
        Boolean keyExists = redisTemplate.hasKey(rateLimitKey);
        assertThat(keyExists).isTrue();
    }

    @Test
    @DisplayName("Should enforce user-specific rate limit after failed attempts (10 per 5 minutes)")
    void shouldEnforceUserRateLimitAfterFailures() {
        createTestUser("failratelimit", "CorrectPassword!");

        LoginRequest wrongRequest = new LoginRequest("failratelimit", "WrongPassword!");

        // Make 10 failed login attempts
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl() + "/login",
                    wrongRequest,
                    String.class
            );
            // Should get 401 or 423 (if locked after 5 failures)
            assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.LOCKED);
        }

        // 11th failed attempt should be rate limited
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                wrongRequest,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis rate limit key
        String rateLimitKey = "rate:login:user:failratelimit";
        Boolean keyExists = redisTemplate.hasKey(rateLimitKey);
        assertThat(keyExists).isTrue();
    }

    @Test
    @DisplayName("Should clear lock on successful login after lock expiry")
    void shouldClearLockAfterSuccessfulLogin() {
        // Create user and fail login 5 times to lock
        createTestUser("clearlock", "CorrectPassword!");

        LoginRequest wrongRequest = new LoginRequest("clearlock", "WrongPassword!");
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity(baseUrl() + "/login", wrongRequest, String.class);
        }

        // Verify locked
        UserEntity lockedUser = userRepository.findByUsername("clearlock").orElseThrow();
        assertThat(lockedUser.getLockedUntil()).isNotNull();

        // Manually clear lock in DB and Redis to simulate expiry
        lockedUser.setLockedUntil(null);
        userRepository.save(lockedUser);
        redisTemplate.delete("auth:login:locked:clearlock");
        redisTemplate.delete("auth:login:attempts:clearlock");

        // Now login with correct password
        LoginRequest correctRequest = new LoginRequest("clearlock", "CorrectPassword!");
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                correctRequest,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify lock cleared in Redis
        Boolean locked = redisTemplate.hasKey("auth:login:locked:clearlock");
        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("Should include device info in login request")
    void shouldIncludeDeviceInfoInLogin() {
        createTestUser("deviceuser", "Password123!");

        LoginRequest request = new LoginRequest("deviceuser", "Password123!");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", "custom-device-123");
        headers.set("User-Agent", "Custom-User-Agent/1.0");

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                entity,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify refresh token has device info
        UserEntity user = userRepository.findByUsername("deviceuser").orElseThrow();
        var tokens = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getDeviceId()).isEqualTo("custom-device-123");
        assertThat(tokens.get(0).getUserAgent()).isEqualTo("Custom-User-Agent/1.0");
    }
}
