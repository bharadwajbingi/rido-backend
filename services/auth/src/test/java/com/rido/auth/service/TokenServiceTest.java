package com.rido.auth.service;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtKeyStore keyStore;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private AuditLogService auditLogService;

    private TokenService tokenService;

    private KeyPair testKeyPair;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ROLE = "USER";
    private static final String DEVICE_ID = "device-123";
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final long ACCESS_TTL = 900;
    private static final long REFRESH_TTL = 86400;
    private static final int MAX_SESSIONS = 5;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        testKeyPair = keyGen.generateKeyPair();

        when(jwtConfig.accessTokenTtlSeconds()).thenReturn(ACCESS_TTL);
        when(jwtConfig.refreshTokenTtlSeconds()).thenReturn(REFRESH_TTL);

        tokenService = new TokenService(
                refreshTokenRepository,
                keyStore,
                jwtConfig,
                auditLogService,
                MAX_SESSIONS
        );
    }

    @Nested
    @DisplayName("Token Creation Tests")
    class TokenCreation {

        @Test
        @DisplayName("Should create tokens with valid input")
        void shouldCreateTokens_whenValidInput() {
            when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(keyStore.getCurrentKeyPair()).thenReturn(testKeyPair);
            when(keyStore.getCurrentKid()).thenReturn("kid-123");

            TokenResponse result = tokenService.createTokens(USER_ID, ROLE, DEVICE_ID, IP, USER_AGENT);

            assertThat(result.getAccessToken()).isNotBlank();
            assertThat(result.getRefreshToken()).isNotBlank();
            assertThat(result.getExpiresIn()).isEqualTo(ACCESS_TTL);
        }

        @Test
        @DisplayName("Should save refresh token to repository")
        void shouldSaveRefreshToken_toRepository() {
            when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(USER_ID))
                    .thenReturn(Collections.emptyList());
            when(keyStore.getCurrentKeyPair()).thenReturn(testKeyPair);
            when(keyStore.getCurrentKid()).thenReturn("kid-123");

            tokenService.createTokens(USER_ID, ROLE, DEVICE_ID, IP, USER_AGENT);

            ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshTokenEntity saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
            assertThat(saved.getIp()).isEqualTo(IP);
            assertThat(saved.getUserAgent()).isEqualTo(USER_AGENT);
            assertThat(saved.isRevoked()).isFalse();
        }
    }

    @Nested
    @DisplayName("Session Limit Tests")
    class SessionLimit {

        @Test
        @DisplayName("Should not revoke sessions when under limit")
        void shouldNotRevokeSessions_whenUnderLimit() {
            List<RefreshTokenEntity> activeSessions = List.of(
                    createActiveSession(),
                    createActiveSession()
            );
            when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(USER_ID))
                    .thenReturn(activeSessions);
            when(keyStore.getCurrentKeyPair()).thenReturn(testKeyPair);
            when(keyStore.getCurrentKid()).thenReturn("kid-123");

            tokenService.createTokens(USER_ID, ROLE, DEVICE_ID, IP, USER_AGENT);

            verify(refreshTokenRepository, times(1)).save(any(RefreshTokenEntity.class));
        }

        @Test
        @DisplayName("Should revoke oldest sessions when limit exceeded")
        void shouldRevokeOldestSessions_whenLimitExceeded() {
            RefreshTokenEntity oldestSession = createActiveSession();
            List<RefreshTokenEntity> activeSessions = List.of(
                    oldestSession,
                    createActiveSession(),
                    createActiveSession(),
                    createActiveSession(),
                    createActiveSession()
            );
            when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(USER_ID))
                    .thenReturn(activeSessions);
            when(keyStore.getCurrentKeyPair()).thenReturn(testKeyPair);
            when(keyStore.getCurrentKid()).thenReturn("kid-123");

            tokenService.createTokens(USER_ID, ROLE, DEVICE_ID, IP, USER_AGENT);

            verify(refreshTokenRepository, atLeast(2)).save(any(RefreshTokenEntity.class));
            assertThat(oldestSession.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("Should log session revocation when limit exceeded")
        void shouldLogSessionRevoked_whenLimitExceeded() {
            RefreshTokenEntity oldestSession = createActiveSession();
            List<RefreshTokenEntity> activeSessions = List.of(
                    oldestSession,
                    createActiveSession(),
                    createActiveSession(),
                    createActiveSession(),
                    createActiveSession()
            );
            when(refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(USER_ID))
                    .thenReturn(activeSessions);
            when(keyStore.getCurrentKeyPair()).thenReturn(testKeyPair);
            when(keyStore.getCurrentKid()).thenReturn("kid-123");

            tokenService.createTokens(USER_ID, ROLE, DEVICE_ID, IP, USER_AGENT);

            verify(auditLogService).logSessionRevoked(
                    eq(USER_ID),
                    any(),
                    eq(oldestSession.getId()),
                    eq("SESSION_LIMIT_EXCEEDED"),
                    eq(DEVICE_ID),
                    eq(IP)
            );
        }

        private RefreshTokenEntity createActiveSession() {
            RefreshTokenEntity rt = new RefreshTokenEntity();
            rt.setId(UUID.randomUUID());
            rt.setUserId(USER_ID);
            rt.setRevoked(false);
            return rt;
        }
    }
}
