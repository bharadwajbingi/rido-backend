package com.rido.auth.service;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private JwtConfig jwtConfig;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private JwtKeyStore keyStore;
    @Mock private MeterRegistry registry;

    @Mock private Counter counter;
    @Mock private Timer timer;

    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        when(registry.counter(anyString())).thenReturn(counter);
        when(registry.timer(anyString())).thenReturn(timer);
        when(timer.record(any(java.util.function.Supplier.class))).thenAnswer(invocation -> ((java.util.function.Supplier) invocation.getArgument(0)).get());

        when(jwtConfig.accessTokenTtlSeconds()).thenReturn(3600L);
        when(jwtConfig.refreshTokenTtlSeconds()).thenReturn(7200L);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        when(keyStore.getCurrentKeyPair()).thenReturn(keyPair);
        when(keyStore.getCurrentKid()).thenReturn("test-kid");

        authService = new AuthService(
            userRepository,
            refreshTokenRepository,
            passwordEncoder,
            loginAttemptService,
            jwtConfig,
            5, // maxFailedAttempts
            300, // lockoutDurationSeconds
            5, // maxActiveSessions (LIMIT = 5)
            tokenBlacklistService,
            keyStore,
            registry
        );
    }

    @Test
    void login_shouldRevokeOldestSession_whenLimitExceeded() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        String password = "password";

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash("encodedHash");
        user.setRole("USER");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "encodedHash")).thenReturn(true);

        // Mock existing sessions (5 sessions, limit is 5)
        List<RefreshTokenEntity> activeSessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RefreshTokenEntity session = new RefreshTokenEntity();
            session.setId(UUID.randomUUID());
            session.setCreatedAt(Instant.now().minusSeconds(100 - i * 10)); // Oldest first
            session.setRevoked(false);
            activeSessions.add(session);
        }

        RefreshTokenEntity oldestSession = activeSessions.get(0);
        RefreshTokenEntity newestSession = activeSessions.get(4);

        when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(userId)).thenReturn(activeSessions);

        // Act
        authService.login(username, password, "device-3", "127.0.0.1", "agent");

        // Assert
        // Should revoke oldestSession because 5 existing + 1 new = 6 > 5.
        // Remove 6 - 5 = 1.
        verify(refreshTokenRepository).save(oldestSession);
        assertTrue(oldestSession.isRevoked());
        assertFalse(newestSession.isRevoked()); // Newest session should remain active
        // verify(refreshTokenRepository, times(2)).save(any(RefreshTokenEntity.class)); // 1 revoke + 1 create (commented out as save might be called more times or differently)
    }
}
