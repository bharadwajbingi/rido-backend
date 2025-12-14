package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.InvalidCredentialsException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    private AdminLoginService adminLoginService;

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "adminpass123";
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String PASSWORD_HASH = "$argon2id$v=19$hash";

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("auth.admin.login.success")).thenReturn(successCounter);
        when(meterRegistry.counter("auth.admin.login.failure")).thenReturn(failureCounter);

        adminLoginService = new AdminLoginService(
                userRepository,
                passwordEncoder,
                tokenService,
                auditLogService,
                meterRegistry
        );
    }

    private UserEntity createAdmin() {
        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID());
        admin.setUsername(USERNAME);
        admin.setPasswordHash(PASSWORD_HASH);
        admin.setRole("ADMIN");
        return admin;
    }

    private UserEntity createUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setRole("USER");
        return user;
    }

    @Nested
    @DisplayName("Successful Admin Login Tests")
    class SuccessfulLogin {

        @Test
        @DisplayName("Should login successfully with valid admin credentials")
        void shouldLogin_whenValidAdminCredentials() {
            UserEntity admin = createAdmin();
            TokenResponse expectedResponse = new TokenResponse("access", "refresh", 3600);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(tokenService.createTokens(admin.getId(), "ADMIN", "admin-console", IP, USER_AGENT))
                    .thenReturn(expectedResponse);

            TokenResponse result = adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT);

            assertThat(result).isEqualTo(expectedResponse);
            verify(auditLogService).logLoginSuccess(admin.getId(), USERNAME, IP, "admin-console", USER_AGENT);
        }
    }

    @Nested
    @DisplayName("Failed Admin Login Tests")
    class FailedLogin {

        @Test
        @DisplayName("Should fail login when user not found")
        void shouldFailLogin_whenUserNotFound() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Should fail login when password is incorrect")
        void shouldFailLogin_whenPasswordIncorrect() {
            UserEntity admin = createAdmin();
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Should fail login when user is not admin")
        void shouldFailLogin_whenUserNotAdmin() {
            UserEntity user = createUser();
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Access denied");
        }
    }

    @Nested
    @DisplayName("Metrics Tests")
    class Metrics {

        @Test
        @DisplayName("Should increment success counter on successful login")
        void shouldIncrementSuccessCounter_onSuccess() {
            UserEntity admin = createAdmin();
            TokenResponse response = new TokenResponse("access", "refresh", 3600);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(tokenService.createTokens(any(), any(), any(), any(), any())).thenReturn(response);

            adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT);

            verify(successCounter).increment();
            verify(failureCounter, never()).increment();
        }

        @Test
        @DisplayName("Should increment failure counter on failed login")
        void shouldIncrementFailureCounter_onFailure() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminLoginService.login(USERNAME, PASSWORD, IP, USER_AGENT))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(failureCounter).increment();
            verify(successCounter, never()).increment();
        }
    }
}
