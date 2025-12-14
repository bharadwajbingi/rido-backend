package com.rido.auth.service;

import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String IP = "192.168.1.1";
    private static final String PASSWORD_HASH = "$argon2id$v=19$hash";

    @Nested
    @DisplayName("User Registration Tests")
    class UserRegistration {

        @Test
        @DisplayName("Should register user with valid input")
        void shouldRegister_whenValidInput() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            UserEntity result = userRegistrationService.register(USERNAME, PASSWORD, IP);

            assertThat(result.getUsername()).isEqualTo(USERNAME);
            assertThat(result.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(result.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Should fail registration when username exists")
        void shouldFailRegister_whenUsernameExists() {
            UserEntity existingUser = new UserEntity();
            existingUser.setUsername(USERNAME);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> userRegistrationService.register(USERNAME, PASSWORD, IP))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasMessage("Username already exists");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should hash password on registration")
        void shouldHashPassword_onRegistration() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            userRegistrationService.register(USERNAME, PASSWORD, IP);

            verify(passwordEncoder).encode(PASSWORD);
            verify(userRepository).save(argThat(user -> PASSWORD_HASH.equals(user.getPasswordHash())));
        }

        @Test
        @DisplayName("Should set role as USER on registration")
        void shouldSetRoleAsUser_onRegistration() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            userRegistrationService.register(USERNAME, PASSWORD, IP);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Should log audit event on registration")
        void shouldLogAudit_onRegistration() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            UserEntity result = userRegistrationService.register(USERNAME, PASSWORD, IP);

            verify(auditLogService).logSignup(result.getId(), USERNAME, IP);
        }
    }

    @Nested
    @DisplayName("Admin Creation Tests")
    class AdminCreation {

        private static final String CREATOR_USERNAME = "existingadmin";

        @Test
        @DisplayName("Should create admin with valid input")
        void shouldCreateAdmin_whenValidInput() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            UserEntity result = userRegistrationService.createAdmin(USERNAME, PASSWORD, IP, CREATOR_USERNAME);

            assertThat(result.getUsername()).isEqualTo(USERNAME);
            assertThat(result.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should fail admin creation when username exists")
        void shouldFailCreateAdmin_whenUsernameExists() {
            UserEntity existingUser = new UserEntity();
            existingUser.setUsername(USERNAME);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> userRegistrationService.createAdmin(USERNAME, PASSWORD, IP, CREATOR_USERNAME))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Should set role as ADMIN on admin creation")
        void shouldSetRoleAsAdmin_onAdminCreation() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            userRegistrationService.createAdmin(USERNAME, PASSWORD, IP, CREATOR_USERNAME);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should log audit event on admin creation")
        void shouldLogAudit_onAdminCreation() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);

            userRegistrationService.createAdmin(USERNAME, PASSWORD, IP, CREATOR_USERNAME);

            verify(auditLogService).logAdminCreation(isNull(), eq(CREATOR_USERNAME), eq(USERNAME), eq(IP));
        }
    }
}
