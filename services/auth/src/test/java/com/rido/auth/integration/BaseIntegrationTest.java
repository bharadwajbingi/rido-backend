package com.rido.auth.integration;

import com.rido.auth.dto.LoginRequest;
import com.rido.auth.dto.RegisterRequest;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.AuditLogRepository;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.Set;

/**
 * Base class for all integration tests.
 * 
 * Provides:
 * - Shared Testcontainers (Postgres + Redis)
 * - Dynamic property injection
 * - Database and Redis cleanup between tests
 * - Utility methods for common test operations
 * 
 * Tests execute sequentially to avoid race conditions with shared containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
@Execution(ExecutionMode.SAME_THREAD)
@Tag("integration")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected EntityManager entityManager;

    // =========================================================================
    // TESTCONTAINERS
    // =========================================================================

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine")
    )
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    @Container
    protected static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    )
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Postgres
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @BeforeEach
    void cleanupDatabase() {
        // Truncate tables in correct order (respecting foreign keys)
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void cleanupRedis() {
        // Clear all Redis keys
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Get base URL for auth service
     */
    protected String baseUrl() {
        return "http://localhost:" + port + "/auth";
    }

    /**
     * Create a test user directly in the database
     */
    @Transactional
    protected UserEntity createTestUser(String username, String password) {
        // This will be implemented by calling the registration endpoint
        // to ensure proper password hashing
        RegisterRequest request = new RegisterRequest(username, password);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                TokenResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create test user: " + response.getStatusCode());
        }

        Optional<UserEntity> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new RuntimeException("User not created: " + username);
        }

        return user.get();
    }

    /**
     * Login and get tokens
     */
    protected TokenResponse loginAndGetTokens(String username, String password) {
        return loginAndGetTokens(username, password, "test-device-id", "Test-User-Agent");
    }

    /**
     * Login with custom device info
     */
    protected TokenResponse loginAndGetTokens(String username, String password, String deviceId, String userAgent) {
        // LoginRequest record requires 5 parameters: username, password, deviceId, ip, userAgent
        // Note: deviceId and userAgent are also passed via headers (controller uses headers, not request body)
        LoginRequest request = new LoginRequest(username, password, null, null, null);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId);
        headers.set("User-Agent", userAgent);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                baseUrl() + "/login",
                entity,
                TokenResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Login failed: " + response.getStatusCode());
        }

        return response.getBody();
    }

    /**
     * Create HTTP headers with Bearer token
     */
    protected HttpHeaders headersWithAuth(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }

    /**
     * Create HTTP headers with device info
     */
    protected HttpHeaders headersWithDevice(String deviceId, String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId);
        headers.set("User-Agent", userAgent);
        return headers;
    }

    /**
     * Create HTTP headers with both auth and device info
     */
    protected HttpHeaders headersWithAuthAndDevice(String accessToken, String deviceId, String userAgent) {
        HttpHeaders headers = headersWithAuth(accessToken);
        headers.set("X-Device-Id", deviceId);
        headers.set("User-Agent", userAgent);
        return headers;
    }
}
