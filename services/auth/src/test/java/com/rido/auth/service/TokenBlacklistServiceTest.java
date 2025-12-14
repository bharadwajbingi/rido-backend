package com.rido.auth.service;

import com.rido.auth.crypto.JwtKeyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private JwtKeyStore keyStore;

    private MeterRegistry meterRegistry;

    private TokenBlacklistService tokenBlacklistService;

    private KeyPair testKeyPair;
    private String testKid;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        testKeyPair = keyGen.generateKeyPair();
        testKid = UUID.randomUUID().toString();

        meterRegistry = new SimpleMeterRegistry();

        tokenBlacklistService = new TokenBlacklistService(redis, keyStore, meterRegistry);
    }

    private String createValidJwtStructure(String kid, String jti, long expSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}").getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"jti\":\"" + jti + "\",\"exp\":" + expSeconds + "}").getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes());
        return header + "." + payload + "." + signature;
    }

    @Nested
    @DisplayName("Blacklist Input Validation Tests")
    class BlacklistInputValidation {

        @Test
        @DisplayName("Should skip blacklist when token is null")
        void shouldSkipBlacklist_whenTokenNull() {
            tokenBlacklistService.blacklist(null);

            verifyNoInteractions(redis);
        }

        @Test
        @DisplayName("Should skip blacklist when token is blank")
        void shouldSkipBlacklist_whenTokenBlank() {
            tokenBlacklistService.blacklist("   ");

            verifyNoInteractions(redis);
        }

        @Test
        @DisplayName("Should skip blacklist when token has invalid format")
        void shouldSkipBlacklist_whenInvalidFormat() {
            tokenBlacklistService.blacklist("not-a-jwt");

            verifyNoInteractions(redis);
        }

        @Test
        @DisplayName("Should skip blacklist when token has only two parts")
        void shouldSkipBlacklist_whenOnlyTwoParts() {
            tokenBlacklistService.blacklist("header.payload");

            verifyNoInteractions(redis);
        }
    }

    @Nested
    @DisplayName("Blacklist Token Validation Tests")
    class BlacklistTokenValidation {

        @Test
        @DisplayName("Should skip blacklist when kid is missing")
        void shouldSkipBlacklist_whenMissingKid() {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\"}".getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"jti\":\"123\",\"exp\":999999}".getBytes());
            String token = header + "." + payload + ".signature";

            tokenBlacklistService.blacklist(token);

            verifyNoInteractions(redis);
        }

        @Test
        @DisplayName("Should skip blacklist when kid is unknown")
        void shouldSkipBlacklist_whenUnknownKid() {
            String token = createValidJwtStructure("unknown-kid", "jti-123", Instant.now().plusSeconds(3600).getEpochSecond());
            when(keyStore.getKeyPair("unknown-kid")).thenReturn(null);

            tokenBlacklistService.blacklist(token);

            verify(redis, never()).opsForValue();
        }

        @Test
        @DisplayName("Should skip blacklist when jti is missing")
        void shouldSkipBlacklist_whenMissingJti() {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("{\"alg\":\"RS256\",\"kid\":\"" + testKid + "\"}").getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"exp\":999999}".getBytes());
            String token = header + "." + payload + ".signature";

            when(keyStore.getKeyPair(testKid)).thenReturn(testKeyPair);

            tokenBlacklistService.blacklist(token);

            verify(redis, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("Successful Blacklist Tests")
    class SuccessfulBlacklist {

        @Test
        @DisplayName("Should blacklist valid token")
        void shouldBlacklist_whenValidToken() {
            String jti = UUID.randomUUID().toString();
            long exp = Instant.now().plusSeconds(3600).getEpochSecond();
            String token = createValidJwtStructure(testKid, jti, exp);

            when(keyStore.getKeyPair(testKid)).thenReturn(testKeyPair);
            when(redis.opsForValue()).thenReturn(valueOps);

            tokenBlacklistService.blacklist(token);

            verify(valueOps).set(eq("auth:jti:blacklist:" + jti), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("IsBlacklisted Tests")
    class IsBlacklisted {

        @Test
        @DisplayName("Should return true when JTI is blacklisted")
        void shouldReturnTrue_whenJtiBlacklisted() {
            String jti = "test-jti";
            when(redis.hasKey("auth:jti:blacklist:" + jti)).thenReturn(true);

            boolean result = tokenBlacklistService.isBlacklisted(jti);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when JTI is not blacklisted")
        void shouldReturnFalse_whenJtiNotBlacklisted() {
            String jti = "test-jti";
            when(redis.hasKey("auth:jti:blacklist:" + jti)).thenReturn(false);

            boolean result = tokenBlacklistService.isBlacklisted(jti);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when Redis returns null")
        void shouldReturnFalse_whenRedisReturnsNull() {
            String jti = "test-jti";
            when(redis.hasKey("auth:jti:blacklist:" + jti)).thenReturn(null);

            boolean result = tokenBlacklistService.isBlacklisted(jti);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Fallback Tests")
    class Fallback {

        @Test
        @DisplayName("Should not throw on blacklist fallback")
        void shouldNotThrow_onBlacklistFallback() {
            assertThatCode(() -> tokenBlacklistService.blacklistFallback("token", new RuntimeException("Redis down")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should fail open on isBlacklisted fallback")
        void shouldFailOpen_whenRedisDown_onCheck() {
            boolean result = tokenBlacklistService.isBlacklistedFallback("jti", new RuntimeException("Redis down"));

            assertThat(result).isFalse();
        }
    }
}
