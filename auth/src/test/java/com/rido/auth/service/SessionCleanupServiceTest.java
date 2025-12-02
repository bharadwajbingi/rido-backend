package com.rido.auth.service;

import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SessionCleanupService.class)
@ActiveProfiles("test")
class SessionCleanupServiceTest {

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionCleanupService cleanupService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        // Clean all tokens before each test
        refreshTokenRepository.deleteAll();
    }

    @Test
    void cleanup_shouldDeleteExpiredTokens() {
        // Arrange: Create an expired token
        RefreshTokenEntity expiredToken = new RefreshTokenEntity();
        expiredToken.setId(UUID.randomUUID());
        expiredToken.setUserId(UUID.randomUUID());
        expiredToken.setTokenHash("expired-hash");
        expiredToken.setExpiresAt(Instant.now().minusSeconds(3600)); // Expired 1 hour ago
        expiredToken.setRevoked(false);
        expiredToken.setCreatedAt(Instant.now().minusSeconds(7200));
        expiredToken.setDeviceId("device-1");
        expiredToken.setIp("127.0.0.1");
        expiredToken.setUserAgent("test-agent");
        refreshTokenRepository.save(expiredToken);

        // Create a valid token (should NOT be deleted)
        RefreshTokenEntity validToken = new RefreshTokenEntity();
        validToken.setId(UUID.randomUUID());
        validToken.setUserId(UUID.randomUUID());
        validToken.setTokenHash("valid-hash");
        validToken.setExpiresAt(Instant.now().plusSeconds(3600)); // Expires in 1 hour
        validToken.setRevoked(false);
        validToken.setCreatedAt(Instant.now());
        validToken.setDeviceId("device-2");
        validToken.setIp("127.0.0.1");
        validToken.setUserAgent("test-agent");
        refreshTokenRepository.save(validToken);

        // Act: Run cleanup
        cleanupService.cleanup();

        // Assert: Expired token should be deleted, valid token should remain
        List<RefreshTokenEntity> remainingTokens = refreshTokenRepository.findAll();
        assertEquals(1, remainingTokens.size());
        assertEquals("valid-hash", remainingTokens.get(0).getTokenHash());
    }

    @Test
    void cleanup_shouldDeleteRevokedTokens() {
        // Arrange: Create a revoked token (even if not expired)
        RefreshTokenEntity revokedToken = new RefreshTokenEntity();
        revokedToken.setId(UUID.randomUUID());
        revokedToken.setUserId(UUID.randomUUID());
        revokedToken.setTokenHash("revoked-hash");
        revokedToken.setExpiresAt(Instant.now().plusSeconds(3600)); // Still valid expiry
        revokedToken.setRevoked(true); // But revoked
        revokedToken.setCreatedAt(Instant.now());
        revokedToken.setDeviceId("device-1");
        revokedToken.setIp("127.0.0.1");
        revokedToken.setUserAgent("test-agent");
        refreshTokenRepository.save(revokedToken);

        // Create a valid token (should NOT be deleted)
        RefreshTokenEntity validToken = new RefreshTokenEntity();
        validToken.setId(UUID.randomUUID());
        validToken.setUserId(UUID.randomUUID());
        validToken.setTokenHash("valid-hash");
        validToken.setExpiresAt(Instant.now().plusSeconds(3600));
        validToken.setRevoked(false);
        validToken.setCreatedAt(Instant.now());
        validToken.setDeviceId("device-2");
        validToken.setIp("127.0.0.1");
        validToken.setUserAgent("test-agent");
        refreshTokenRepository.save(validToken);

        // Act: Run cleanup
        cleanupService.cleanup();

        // Assert: Revoked token should be deleted, valid token should remain
        List<RefreshTokenEntity> remainingTokens = refreshTokenRepository.findAll();
        assertEquals(1, remainingTokens.size());
        assertEquals("valid-hash", remainingTokens.get(0).getTokenHash());
    }

    @Test
    void cleanup_shouldDeleteBothExpiredAndRevokedTokens() {
        // Arrange: Create multiple tokens with different states
        RefreshTokenEntity expiredToken = createToken("expired-hash", 
            Instant.now().minusSeconds(3600), false);
        RefreshTokenEntity revokedToken = createToken("revoked-hash", 
            Instant.now().plusSeconds(3600), true);
        RefreshTokenEntity expiredAndRevokedToken = createToken("expired-revoked-hash", 
            Instant.now().minusSeconds(3600), true);
        RefreshTokenEntity validToken = createToken("valid-hash", 
            Instant.now().plusSeconds(3600), false);

        refreshTokenRepository.saveAll(List.of(
            expiredToken, revokedToken, expiredAndRevokedToken, validToken
        ));

        // Act: Run cleanup
        cleanupService.cleanup();

        // Assert: Only valid token should remain
        List<RefreshTokenEntity> remainingTokens = refreshTokenRepository.findAll();
        assertEquals(1, remainingTokens.size());
        assertEquals("valid-hash", remainingTokens.get(0).getTokenHash());
    }

    @Test
    void cleanup_shouldNotDeleteValidTokens() {
        // Arrange: Create only valid tokens
        RefreshTokenEntity validToken1 = createToken("valid-1", 
            Instant.now().plusSeconds(3600), false);
        RefreshTokenEntity validToken2 = createToken("valid-2", 
            Instant.now().plusSeconds(7200), false);
        RefreshTokenEntity validToken3 = createToken("valid-3", 
            Instant.now().plusSeconds(10800), false);

        refreshTokenRepository.saveAll(List.of(validToken1, validToken2, validToken3));

        // Act: Run cleanup
        cleanupService.cleanup();

        // Assert: All valid tokens should remain
        List<RefreshTokenEntity> remainingTokens = refreshTokenRepository.findAll();
        assertEquals(3, remainingTokens.size());
    }

    @Test
    void cleanup_shouldHandleEmptyDatabase() {
        // Arrange: No tokens in database

        // Act: Run cleanup (should not throw exception)
        assertDoesNotThrow(() -> cleanupService.cleanup());

        // Assert: No tokens should exist
        List<RefreshTokenEntity> remainingTokens = refreshTokenRepository.findAll();
        assertEquals(0, remainingTokens.size());
    }

    // Helper method to create test tokens
    private RefreshTokenEntity createToken(String tokenHash, Instant expiresAt, boolean revoked) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(UUID.randomUUID());
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        token.setCreatedAt(Instant.now());
        token.setDeviceId("device-" + UUID.randomUUID().toString().substring(0, 8));
        token.setIp("127.0.0.1");
        token.setUserAgent("test-agent");
        return token;
    }
}
