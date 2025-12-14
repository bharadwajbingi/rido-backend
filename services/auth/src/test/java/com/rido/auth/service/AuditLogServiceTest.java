package com.rido.auth.service;

import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.repo.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "testuser";
    private static final String IP = "192.168.1.1";
    private static final String DEVICE_ID = "device-123";
    private static final String USER_AGENT = "Mozilla/5.0";

    @Nested
    @DisplayName("Login Event Tests")
    class LoginEvents {

        @Test
        @DisplayName("Should log login success with correct event type")
        void shouldLogLoginSuccess_withCorrectFields() {
            auditLogService.logLoginSuccess(USER_ID, USERNAME, IP, DEVICE_ID, USER_AGENT);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.LOGIN_SUCCESS);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("Should log login failed with correct event type")
        void shouldLogLoginFailed_withCorrectFields() {
            auditLogService.logLoginFailed(USERNAME, IP, DEVICE_ID, USER_AGENT, "Invalid credentials");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.LOGIN_FAILED);
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
        }
    }

    @Nested
    @DisplayName("Session Event Tests")
    class SessionEvents {

        @Test
        @DisplayName("Should log logout with correct event type")
        void shouldLogLogout_withCorrectFields() {
            auditLogService.logLogout(USER_ID, USERNAME, IP, DEVICE_ID);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.LOGOUT);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should log refresh with correct event type")
        void shouldLogRefresh_withCorrectFields() {
            auditLogService.logRefresh(USER_ID, USERNAME, IP, DEVICE_ID);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Should log session revoked with correct event type")
        void shouldLogSessionRevoked_withCorrectFields() {
            UUID sessionId = UUID.randomUUID();
            auditLogService.logSessionRevoked(USER_ID, USERNAME, sessionId, "SESSION_LIMIT_EXCEEDED", DEVICE_ID, IP);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.SESSION_REVOKED);
        }
    }

    @Nested
    @DisplayName("Signup Event Tests")
    class SignupEvents {

        @Test
        @DisplayName("Should log signup with correct event type")
        void shouldLogSignup_withCorrectFields() {
            auditLogService.logSignup(USER_ID, USERNAME, IP);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.SIGNUP);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("Admin Event Tests")
    class AdminEvents {

        @Test
        @DisplayName("Should log key rotation with correct event type")
        void shouldLogKeyRotation_withCorrectFields() {
            auditLogService.logKeyRotation(USER_ID, USERNAME);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.KEY_ROTATION);
        }

        @Test
        @DisplayName("Should log admin creation with correct event type")
        void shouldLogAdminCreation_withCorrectFields() {
            String newAdminUsername = "newadmin";
            auditLogService.logAdminCreation(USER_ID, USERNAME, newAdminUsername, IP);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.ADMIN_CREATION);
        }
    }

    @Nested
    @DisplayName("Security Event Tests")
    class SecurityEvents {

        @Test
        @DisplayName("Should log device mismatch with correct event type")
        void shouldLogDeviceMismatch_withCorrectFields() {
            auditLogService.logDeviceMismatch(
                    USER_ID, USERNAME, IP,
                    "old-device", "new-device",
                    "old-agent", "new-agent"
            );

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo(AuditEvent.DEVICE_MISMATCH);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
        }
    }
}
