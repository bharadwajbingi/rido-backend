package com.rido.auth.service;

import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter attemptCounter;

    @Mock
    private Counter blockedCounter;

    @Mock
    private Counter hardLockCounter;

    private LoginAttemptService loginAttemptService;

    private static final String USERNAME = "testuser";
    private static final String IP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("auth.login.attempt")).thenReturn(attemptCounter);
        when(meterRegistry.counter("auth.login.blocked")).thenReturn(blockedCounter);
        when(meterRegistry.counter("auth.login.hardlock")).thenReturn(hardLockCounter);

        loginAttemptService = new LoginAttemptService(redis, userRepository, meterRegistry);
    }

    private UserEntity createUser(String role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setRole(role);
        return user;
    }

    @Nested
    @DisplayName("EnsureNotLocked Tests")
    class EnsureNotLocked {

        @Test
        @DisplayName("Should throw when locked in Redis")
        void shouldThrow_whenLockedInRedis() {
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:login:locked:" + USERNAME)).thenReturn("1");

            assertThatThrownBy(() -> loginAttemptService.ensureNotLocked(USERNAME))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessageContaining("Account locked");
        }

        @Test
        @DisplayName("Should throw when locked in DB")
        void shouldThrow_whenLockedInDb() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now().plusSeconds(3600));

            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:login:locked:" + USERNAME)).thenReturn(null);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> loginAttemptService.ensureNotLocked(USERNAME))
                    .isInstanceOf(AccountLockedException.class);
        }

        @Test
        @DisplayName("Should auto-unlock when lock has expired")
        void shouldAutoUnlock_whenLockExpired() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now().minusSeconds(3600));

            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("auth:login:locked:" + USERNAME)).thenReturn(null);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            loginAttemptService.ensureNotLocked(USERNAME);

            verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
        }

        @Test
        @DisplayName("Should fallback to DB check when Redis is down")
        void shouldFallbackToDbCheck_whenRedisDown() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            loginAttemptService.ensureNotLockedFallback(USERNAME, new RuntimeException("Redis down"));

            verify(userRepository).findByUsername(USERNAME);
        }
    }

    @Nested
    @DisplayName("OnFailure Tests")
    class OnFailure {

        @Test
        @DisplayName("Should increment attempts on failure")
        void shouldIncrementAttempts_onFailure() {
            UserEntity user = createUser("USER");
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("auth:login:attempts:" + USERNAME)).thenReturn(1L);
            when(valueOps.increment("auth:login:ip:attempts:" + IP)).thenReturn(1L);

            loginAttemptService.onFailure(USERNAME, IP, user);

            verify(valueOps).increment("auth:login:attempts:" + USERNAME);
            verify(redis).expire(eq("auth:login:attempts:" + USERNAME), any(Duration.class));
        }

        @Test
        @DisplayName("Should lock account when max attempts exceeded")
        void shouldLockAccount_whenMaxAttemptsExceeded() {
            UserEntity user = createUser("USER");
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("auth:login:attempts:" + USERNAME)).thenReturn(6L);
            when(valueOps.increment("auth:login:ip:attempts:" + IP)).thenReturn(1L);

            assertThatThrownBy(() -> loginAttemptService.onFailure(USERNAME, IP, user))
                    .isInstanceOf(AccountLockedException.class);

            verify(valueOps).set(eq("auth:login:locked:" + USERNAME), eq("1"), any(Duration.class));
            verify(userRepository).save(argThat(u -> u.getLockedUntil() != null));
        }

        @Test
        @DisplayName("Should skip lock for admin users")
        void shouldSkipLock_forAdminUsers() {
            UserEntity admin = createUser("ADMIN");
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("auth:login:attempts:" + USERNAME)).thenReturn(10L);
            when(valueOps.increment("auth:login:ip:attempts:" + IP)).thenReturn(1L);

            loginAttemptService.onFailure(USERNAME, IP, admin);

            verify(valueOps, never()).set(eq("auth:login:locked:" + USERNAME), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Should block IP when IP attempts exceeded")
        void shouldBlockIp_whenIpAttemptsExceeded() {
            UserEntity user = createUser("USER");
            when(redis.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("auth:login:attempts:" + USERNAME)).thenReturn(1L);
            when(valueOps.increment("auth:login:ip:attempts:" + IP)).thenReturn(21L);

            assertThatThrownBy(() -> loginAttemptService.onFailure(USERNAME, IP, user))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessageContaining("Too many failed login attempts from this IP");
        }
    }

    @Nested
    @DisplayName("OnSuccess Tests")
    class OnSuccess {

        @Test
        @DisplayName("Should clear lock on success")
        void shouldClearLock_onSuccess() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now().plusSeconds(3600));

            loginAttemptService.onSuccess(USERNAME, IP, user);

            verify(redis).delete("auth:login:attempts:" + USERNAME);
            verify(redis).delete("auth:login:locked:" + USERNAME);
            verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
        }

        @Test
        @DisplayName("Should fallback to DB lock clear when Redis is down")
        void shouldFallbackToDbLockClear_whenRedisDown() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now());

            loginAttemptService.onSuccessFallback(USERNAME, IP, user, new RuntimeException("Redis down"));

            verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
        }
    }

    @Nested
    @DisplayName("ResetFailures Tests")
    class ResetFailures {

        @Test
        @DisplayName("Should reset all failure state")
        void shouldResetAllFailureState() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now().plusSeconds(3600));
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            loginAttemptService.resetFailures(USERNAME);

            verify(redis).delete("auth:login:attempts:" + USERNAME);
            verify(redis).delete("auth:login:locked:" + USERNAME);
            verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
        }
    }
}
