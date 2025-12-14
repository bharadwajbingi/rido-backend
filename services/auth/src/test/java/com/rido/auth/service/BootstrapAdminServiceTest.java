package com.rido.auth.service;

import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private BootstrapAdminService bootstrapAdminService;

    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "admin123";
    private static final String PASSWORD_HASH = "$argon2id$v=19$hash";

    @BeforeEach
    void setUp() throws Exception {
        bootstrapAdminService = new BootstrapAdminService(
                userRepository,
                passwordEncoder,
                auditLogService
        );
    }

    private void setBootstrapCredentials(String username, String password) throws Exception {
        Field usernameField = BootstrapAdminService.class.getDeclaredField("bootstrapUsername");
        usernameField.setAccessible(true);
        usernameField.set(bootstrapAdminService, username);

        Field passwordField = BootstrapAdminService.class.getDeclaredField("bootstrapPassword");
        passwordField.setAccessible(true);
        passwordField.set(bootstrapAdminService, password);
    }

    @Nested
    @DisplayName("Skip Bootstrap Tests")
    class SkipBootstrap {

        @Test
        @DisplayName("Should skip when password not configured")
        void shouldSkip_whenPasswordNotConfigured() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, "");

            bootstrapAdminService.bootstrapAdmin();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip when password is null")
        void shouldSkip_whenPasswordNull() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, null);

            bootstrapAdminService.bootstrapAdmin();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip when admin already exists")
        void shouldSkip_whenAdminAlreadyExists() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
            when(userRepository.existsByRole("ADMIN")).thenReturn(true);

            bootstrapAdminService.bootstrapAdmin();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip when username already taken")
        void shouldSkip_whenUsernameAlreadyTaken() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
            when(userRepository.existsByRole("ADMIN")).thenReturn(false);
            when(userRepository.findByUsername(BOOTSTRAP_USERNAME))
                    .thenReturn(Optional.of(new UserEntity()));

            bootstrapAdminService.bootstrapAdmin();

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Create Admin Tests")
    class CreateAdmin {

        @Test
        @DisplayName("Should create admin when no admin exists")
        void shouldCreateAdmin_whenNoAdminExists() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
            when(userRepository.existsByRole("ADMIN")).thenReturn(false);
            when(userRepository.findByUsername(BOOTSTRAP_USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(BOOTSTRAP_PASSWORD)).thenReturn(PASSWORD_HASH);

            bootstrapAdminService.bootstrapAdmin();

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());

            UserEntity savedAdmin = captor.getValue();
            assertThat(savedAdmin.getUsername()).isEqualTo(BOOTSTRAP_USERNAME);
            assertThat(savedAdmin.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(savedAdmin.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should log audit event on admin creation")
        void shouldLogAudit_onAdminCreation() throws Exception {
            setBootstrapCredentials(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
            when(userRepository.existsByRole("ADMIN")).thenReturn(false);
            when(userRepository.findByUsername(BOOTSTRAP_USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(BOOTSTRAP_PASSWORD)).thenReturn(PASSWORD_HASH);

            bootstrapAdminService.bootstrapAdmin();

            verify(auditLogService).logAdminCreation(isNull(), eq("system"), eq(BOOTSTRAP_USERNAME), eq("127.0.0.1"));
        }
    }
}
