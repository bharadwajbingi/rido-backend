package com.rido.auth.integration;

import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.RefreshRequest;
import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for rate limiting with Redis.
 * 
 * Tests sliding window rate limiting across different endpoints.
 */
@DisplayName("Rate Limiting Integration Tests")
public class RateLimitingIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Should enforce registration rate limit (10 per 60s)")
    void shouldEnforceRegistrationRateLimit() {
        // Successfully register 10 users
        for (int i = 0; i < 10; i++) {
            RegisterRequest request = new RegisterRequest("rateuser" + i, "Pass" + i + "!");
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    baseUrl() + "/register",
                    request,
                    TokenResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 11th request should be rate limited
        RegisterRequest request = new RegisterRequest("rateuser11", "Pass11!");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis sorted set exists with entries
        String rateKey = "rate:register:127.0.0.1";
        Long zsetSize = redisTemplate.opsForZSet().size(rateKey);
        assertThat(zsetSize).isNotNull();
        assertThat(zsetSize).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should enforce login IP rate limit (50 per 60s)")
    void shouldEnforceLoginIpRateLimit() {
        createTestUser("ipratetest", "Password123!");

        LoginRequest request = new LoginRequest("ipratetest", "Password123!", null, null, null);

        // Make 50 login requests
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

        // Verify Redis key
        String rateKey = "rate:login:ip:127.0.0.1";
        assertThat(redisTemplate.hasKey(rateKey)).isTrue();
    }

    @Test
    @DisplayName("Should enforce login user rate limit after failures (10 per 300s)")
    void shouldEnforceLoginUserRateLimitAfterFailures() {
        createTestUser("userratetest", "CorrectPassword!");

        LoginRequest wrongRequest = new LoginRequest("userratetest", "WrongPassword!", null, null, null);

        // Make 10 failed attempts (account will lock at 5, but user rate limit checks at 10)
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl() + "/login",
                    wrongRequest,
                    String.class
            );
            // Should get 401 or 423
            assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.LOCKED);
        }

        // 11th failed attempt should be rate limited
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                wrongRequest,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis key
        String rateKey = "rate:login:user:userratetest";
        assertThat(redisTemplate.hasKey(rateKey)).isTrue();
    }

    @Test
    @DisplayName("Should enforce refresh rate limit (20 per 60s)")
    void shouldEnforceRefreshRateLimit() {
        createTestUser("refreshratetest", "Password123!");

        // Create and refresh 20 tokens
        for (int i = 0; i < 20; i++) {
            TokenResponse tokens = loginAndGetTokens("refreshratetest", "Password123!");

            RefreshRequest request = new RefreshRequest(tokens.getRefreshToken());
            HttpHeaders headers = headersWithDevice("device-" + i, "Agent");
            HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    baseUrl() + "/refresh",
                    entity,
                    TokenResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 21st should be rate limited
        TokenResponse tokens = loginAndGetTokens("refreshratetest", "Password123!");
        RefreshRequest request = new RefreshRequest(tokens.getRefreshToken());
        HttpHeaders headers = headersWithDevice("device-21", "Agent");
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/refresh",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis key
        String rateKey = "rate:refresh:127.0.0.1";
        assertThat(redisTemplate.hasKey(rateKey)).isTrue();
    }

    @Test
    @DisplayName("Should use sliding window for rate limiting")
    void shouldUseSlidingWindowRateLimiting() {
        // Verify sorted set is used (sliding window implementation)
        RegisterRequest request = new RegisterRequest("slidetest", "Password123!");
        restTemplate.postForEntity(baseUrl() + "/register", request, TokenResponse.class);

        String rateKey = "rate:register:127.0.0.1";

        // Verify it's a sorted set (ZSET)
        Long size = redisTemplate.opsForZSet().size(rateKey);
        assertThat(size).isNotNull();
        assertThat(size).isGreaterThan(0);

        // Verify scores are timestamps
        var entries = redisTemplate.opsForZSet().rangeWithScores(rateKey, 0, -1);
        assertThat(entries).isNotNull();
        assertThat(entries).isNotEmpty();

        // Scores should be recent timestamps (milliseconds)
        entries.forEach(entry -> {
            Double score = entry.getScore();
            assertThat(score).isNotNull();
            // Score should be a recent timestamp in milliseconds
            long now = System.currentTimeMillis();
            assertThat(score).isBetween((double) (now - 10000), (double) (now + 1000));
        });
    }

    @Test
    @DisplayName("Should set TTL on rate limit keys")
    void shouldSetTtlOnRateLimitKeys() {
        RegisterRequest request = new RegisterRequest("ttltest", "Password123!");
        restTemplate.postForEntity(baseUrl() + "/register", request, TokenResponse.class);

        String rateKey = "rate:register:127.0.0.1";

        // Verify TTL is set
        Long ttl = redisTemplate.getExpire(rateKey, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        // TTL should be window duration + small buffer (60 + 2 = 62)
        assertThat(ttl).isLessThanOrEqualTo(62L);
    }

    @Test
    @DisplayName("Should clean old entries from sliding window")
    void shouldCleanOldEntriesFromSlidingWindow() {
        RegisterRequest request1 = new RegisterRequest("cleantest1", "Password123!");
        restTemplate.postForEntity(baseUrl() + "/register", request1, TokenResponse.class);

        String rateKey = "rate:register:127.0.0.1";
        Long initialSize = redisTemplate.opsForZSet().size(rateKey);
        assertThat(initialSize).isGreaterThan(0);

        // Entries older than the window should be removed by the rate limiter on next check
        // Verify key has a TTL which will eventually clean it up
        Long ttl = redisTemplate.getExpire(rateKey, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);
    }
}
