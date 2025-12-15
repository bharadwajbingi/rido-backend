package com.rido.auth.integration;

import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for Resilience4j circuit breaker with Redis.
 * 
 * Tagged as "slow" - excluded from PR CI due to container stop/start flakiness.
 * Run in nightly CI or via: ./gradlew test --tests "*IT"
 * 
 * Tests circuit breaker fail-open behavior when Redis is unavailable.
 */
@DisplayName("Circuit Breaker Integration Tests")
@Tag("slow")
public class CircuitBreakerIT extends BaseIntegrationTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("Should fail-open on rate limit check when Redis is down")
    void shouldFailOpenOnRateLimitWhenRedisDown() {
        createTestUser("circuituser", "Password123!");

        // Stop Redis container
        redis.stop();

        // Rate limit should fail-open (allow request)
        LoginRequest request = new LoginRequest("circuituser", "Password123!", null, null, null);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                request,
                TokenResponse.class
        );

        // Should succeed despite Redis being down (fail-open)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Restart Redis for cleanup
        redis.start();
    }

    @Test
    @DisplayName("Should fail-open on blacklist check when Redis is down")
    void shouldFailOpenOnBlacklistCheckWhenRedisDown() {
        createTestUser("blacklistcircuit", "Password123!");
        TokenResponse tokens = loginAndGetTokens("blacklistcircuit", "Password123!");

        // Logout to blacklist token
        com.rido.auth.dto.LogoutRequest logoutRequest = new com.rido.auth.dto.LogoutRequest(tokens.getRefreshToken());
        HttpHeaders headers = headersWithAuth(tokens.getAccessToken());
        HttpEntity<com.rido.auth.dto.LogoutRequest> entity = new HttpEntity<>(logoutRequest, headers);
        restTemplate.postForEntity(baseUrl() + "/logout", entity, String.class);

        // Extract JTI
        String jti = extractJtiFromToken(tokens.getAccessToken());

        // Verify blacklisted while Redis is up
        boolean blacklistedBeforeStop = tokenBlacklistService.isBlacklisted(jti);
        assertThat(blacklistedBeforeStop).isTrue();

        // Stop Redis
        redis.stop();

        // Blacklist check should fail-open (return false)
        boolean blacklistedAfterStop = tokenBlacklistService.isBlacklisted(jti);
        assertThat(blacklistedAfterStop).isFalse(); // Fail-open behavior

        // Restart Redis
        redis.start();
    }

    @Test
    @DisplayName("Should use DB lock when Redis login attempt tracking fails")
    void shouldUseDbLockWhenRedisDown() {
        createTestUser("dblockuser", "Password123!");

        // Stop Redis
        redis.stop();

        LoginRequest wrongRequest = new LoginRequest("dblockuser", "WrongPassword!", null, null, null);

        // Make 5 failed attempts (should trigger DB lock even without Redis)
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl() + "/login",
                    wrongRequest,
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt should be locked (DB lock should work)
        ResponseEntity<String> lockedResponse = restTemplate.postForEntity(
                baseUrl() + "/login",
                wrongRequest,
                String.class
        );

        assertThat(lockedResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        // Verify DB lock exists
        com.rido.auth.model.UserEntity user = userRepository.findByUsername("dblockuser").orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();

        // Restart Redis
        redis.start();
    }

    @Test
    @DisplayName("Should allow registration when Redis is down (fail-open)")
    void shouldAllowRegistrationWhenRedisDown() {
        // Stop Redis
        redis.stop();

        RegisterRequest request = new RegisterRequest("redisdownreg", "Password123!");
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                TokenResponse.class
        );

        // Should succeed (fail-open)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify user created
        assertThat(userRepository.findByUsername("redisdownreg")).isPresent();

        // Restart Redis
        redis.start();
    }

    @Test
    @DisplayName("Should recover after Redis comes back up")
    void shouldRecoverAfterRedisRestart() {
        createTestUser("recoveryuser", "Password123!");

        // Stop Redis
        redis.stop();

        // Request works (fail-open)
        TokenResponse tokens1 = loginAndGetTokens("recoveryuser", "Password123!");
        assertThat(tokens1).isNotNull();

        // Restart Redis
        redis.start();

        // Wait for Redis to be ready
        await().atMost(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    try {
                        redisTemplate.opsForValue().set("test-key", "test-value");
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });

        // Subsequent requests should work normally with Redis
        TokenResponse tokens2 = loginAndGetTokens("recoveryuser", "Password123!");
        assertThat(tokens2).isNotNull();

        // Verify Redis is working (rate limit key should exist)
        String rateKey = "rate:login:ip:127.0.0.1";
        Boolean keyExists = redisTemplate.hasKey(rateKey);
        assertThat(keyExists).isTrue();
    }

    /**
     * Helper to extract JTI from token
     */
    private String extractJtiFromToken(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        int jtiIndex = payload.indexOf("\"jti\":\"");
        int startIndex = jtiIndex + 7;
        int endIndex = payload.indexOf("\"", startIndex);
        return payload.substring(startIndex, endIndex);
    }
}
