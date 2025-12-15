package com.rido.auth.integration;

import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.model.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for user registration flow.
 * 
 * Tests the complete registration path including:
 * - User creation in Postgres
 * - Password hashing
 * - Refresh token creation
 * - Auto-login after registration
 * - Rate limiting
 * - Audit logging
 */
@DisplayName("User Registration Integration Tests")
public class UserRegistrationIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest("testuser", "SecurePass123!");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                TokenResponse.class
        );

        // Verify HTTP response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(60); // From test profile

        // Verify user persisted in database
        UserEntity user = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.getPasswordHash()).doesNotContain("SecurePass123!"); // Password is hashed
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getLockedUntil()).isNull();

        // Verify refresh token created
        long tokenCount = refreshTokenRepository.count();
        assertThat(tokenCount).isEqualTo(1);

        // Verify audit log created
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).isNotEmpty();
        assertThat(auditLogs).anyMatch(log -> 
                log.getEventType() == AuditEvent.REGISTRATION &&
                log.isSuccess() &&
                log.getUserId().equals(user.getId())
        );
    }

    @Test
    @DisplayName("Should reject duplicate username")
    void shouldRejectDuplicateUsername() {
        // Create first user
        RegisterRequest firstRequest = new RegisterRequest("duplicate", "Password123!");
        restTemplate.postForEntity(baseUrl() + "/register", firstRequest, TokenResponse.class);

        // Try to register same username again
        RegisterRequest duplicateRequest = new RegisterRequest("duplicate", "DifferentPass456!");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                duplicateRequest,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already exists");

        // Verify only one user exists
        long userCount = userRepository.count();
        assertThat(userCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should enforce rate limit on registration")
    void shouldEnforceRateLimitOnRegistration() {
        // Rate limit is 10 requests per 60 seconds per IP
        // Register 10 users successfully
        for (int i = 0; i < 10; i++) {
            RegisterRequest request = new RegisterRequest("user" + i, "Password" + i + "!");
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    baseUrl() + "/register",
                    request,
                    TokenResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 11th request should be rate limited
        RegisterRequest request = new RegisterRequest("user11", "Password11!");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis rate limit key exists
        String rateLimitKey = "rate:register:127.0.0.1";
        Boolean keyExists = redisTemplate.hasKey(rateLimitKey);
        assertThat(keyExists).isTrue();

        // Verify key is a sorted set (sliding window implementation)
        Long size = redisTemplate.opsForZSet().size(rateLimitKey);
        assertThat(size).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should validate password is not empty")
    void shouldValidatePasswordNotEmpty() {
        RegisterRequest request = new RegisterRequest("emptypass", "");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Verify no user created
        assertThat(userRepository.findByUsername("emptypass")).isEmpty();
    }

    @Test
    @DisplayName("Should validate username is not empty")
    void shouldValidateUsernameNotEmpty() {
        RegisterRequest request = new RegisterRequest("", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Verify no user created
        long userCount = userRepository.count();
        assertThat(userCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should auto-login after registration and create session")
    void shouldAutoLoginAfterRegistration() {
        RegisterRequest request = new RegisterRequest("autologin", "Password123!");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                TokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse tokens = response.getBody();
        assertThat(tokens).isNotNull();

        // Verify refresh token exists in database
        UserEntity user = userRepository.findByUsername("autologin").orElseThrow();
        long sessionCount = refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(user.getId()).size();
        assertThat(sessionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should check username availability")
    void shouldCheckUsernameAvailability() {
        // Create a user
        createTestUser("existing", "Password123!");

        // Check existing username
        ResponseEntity<String> existingResponse = restTemplate.getForEntity(
                baseUrl() + "/check-username?username=existing",
                String.class
        );
        assertThat(existingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existingResponse.getBody()).contains("\"available\":false");

        // Check available username
        ResponseEntity<String> availableResponse = restTemplate.getForEntity(
                baseUrl() + "/check-username?username=available",
                String.class
        );
        assertThat(availableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(availableResponse.getBody()).contains("\"available\":true");
    }
}
