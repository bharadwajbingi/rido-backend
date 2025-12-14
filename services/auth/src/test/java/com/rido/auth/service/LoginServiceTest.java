package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private TokenService tokenService;

    @Mock
    private TracingService tracingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    @Mock
    private Counter lockoutCounter;

    @Mock
    private Timer requestTimer;

    private LoginService loginService;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String DEVICE_ID = "device-123";
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String PASSWORD_HASH = "$argon2id$v=19$hash";

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("auth.login.success")).thenReturn(successCounter);
        when(meterRegistry.counter("auth.login.failure")).thenReturn(failureCounter);
        when(meterRegistry.counter("auth.login.lockout")).thenReturn(lockoutCounter);
        when(meterRegistry.timer("auth.request.duration")).thenReturn(requestTimer);

        loginService = new LoginService(
                userRepository,
                passwordEncoder,
                loginAttemptService,
                tokenService,
                tracingService,
                auditLogService,
                meterRegistry
        );
    }

    private UserEntity createUser(String role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setRole(role);
        return user;
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPath {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLogin_whenValidCredentials() {
            UserEntity user = createUser("USER");
            TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(requestTimer.record(any(Supplier.class))).thenAnswer(inv -> {
                Supplier<?> supplier = inv.getArgument(0);
                return supplier.get();
            });
            when(tokenService.createTokens(user.getId(), "USER", DEVICE_ID, IP, USER_AGENT))
                    .thenReturn(expectedResponse);

            TokenResponse result = loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT);

            assertThat(result).isEqualTo(expectedResponse);
            verify(loginAttemptService).ensureNotLocked(USERNAME);
            verify(loginAttemptService).onSuccess(USERNAME, IP, user);
            verify(auditLogService).logLoginSuccess(user.getId(), USERNAME, IP, DEVICE_ID, USER_AGENT);
        }
    }

    @Nested
    @DisplayName("User Not Found Tests")
    class UserNotFound {

        @Test
        @DisplayName("Should fail login when user not found")
        void shouldFailLogin_whenUserNotFound() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.matches(eq(PASSWORD), any())).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid username or password");

            verify(loginAttemptService).ensureNotLocked(USERNAME);
            verify(loginAttemptService).onFailure(eq(USERNAME), eq(IP), isNull());
            verify(auditLogService).logLoginFailed(USERNAME, IP, DEVICE_ID, USER_AGENT, "Invalid credentials");
        }

        @Test
        @DisplayName("Should perform timing attack mitigation when user not found")
        void shouldPerformTimingAttackMitigation_whenUserNotFound() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).matches(eq(PASSWORD), any());
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidation {

        @Test
        @DisplayName("Should fail login when password is incorrect")
        void shouldFailLogin_whenPasswordIncorrect() {
            UserEntity user = createUser("USER");

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid username or password");

            verify(loginAttemptService).onFailure(USERNAME, IP, user);
            verify(auditLogService).logLoginFailed(USERNAME, IP, DEVICE_ID, USER_AGENT, "Invalid credentials");
        }
    }

    @Nested
    @DisplayName("Account Lockout Tests")
    class AccountLockout {

        @Test
        @DisplayName("Should fail login when account is locked in DB")
        void shouldFailLogin_whenAccountLockedInDb() {
            UserEntity user = createUser("USER");
            user.setLockedUntil(Instant.now().plusSeconds(3600));

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessage("Account locked");
        }

        @Test
        @DisplayName("Should skip lock check for admin users")
        void shouldSkipLockCheck_whenUserIsAdmin() {
            UserEntity admin = createUser("ADMIN");
            admin.setLockedUntil(Instant.now().plusSeconds(3600));
            TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(requestTimer.record(any(Supplier.class))).thenAnswer(inv -> {
                Supplier<?> supplier = inv.getArgument(0);
                return supplier.get();
            });
            when(tokenService.createTokens(admin.getId(), "ADMIN", DEVICE_ID, IP, USER_AGENT))
                    .thenReturn(expectedResponse);

            TokenResponse result = loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT);

            assertThat(result).isEqualTo(expectedResponse);
            verify(loginAttemptService, never()).ensureNotLocked(USERNAME);
        }
    }

    @Nested
    @DisplayName("Metrics Tests")
    class Metrics {

        @Test
        @DisplayName("Should increment failure counter on login failure")
        void shouldIncrementFailureCounter_onLoginFailure() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.matches(eq(PASSWORD), any())).thenReturn(false);

            assertThatThrownBy(() -> loginService.login(USERNAME, PASSWORD, DEVICE_ID, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(failureCounter).increment();
        }
    }
}
