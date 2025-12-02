package com.rido.auth.service;

import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import com.rido.auth.repo.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Centralized service for audit logging of security-sensitive actions.
 * Provides methods to log various authentication and authorization events
 * with structured logging and database persistence.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a successful login event
     */
    public void logLoginSuccess(UUID userId, String username, String ip, String deviceId, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.LOGIN_SUCCESS);
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setDeviceId(deviceId);
            auditLog.setUserAgent(userAgent);
            auditLog.setSuccess(true);

            auditLogRepository.save(auditLog);

            log.info("audit_login_success",
                    kv("userId", userId),
                    kv("username", username),
                    kv("ip", ip),
                    kv("deviceId", deviceId)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for login success", e);
        }
    }

    /**
     * Log a failed login attempt
     */
    public void logLoginFailed(String username, String ip, String deviceId, String userAgent, String reason) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.LOGIN_FAILED);
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setDeviceId(deviceId);
            auditLog.setUserAgent(userAgent);
            auditLog.setSuccess(false);
            auditLog.setFailureReason(reason);

            auditLogRepository.save(auditLog);

            log.warn("audit_login_failed",
                    kv("username", username),
                    kv("ip", ip),
                    kv("deviceId", deviceId),
                    kv("reason", reason)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for login failure", e);
        }
    }

    /**
     * Log a logout event
     */
    public void logLogout(UUID userId, String username, String ip, String deviceId) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.LOGOUT);
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setDeviceId(deviceId);
            auditLog.setSuccess(true);

            auditLogRepository.save(auditLog);

            log.info("audit_logout",
                    kv("userId", userId),
                    kv("username", username),
                    kv("ip", ip),
                    kv("deviceId", deviceId)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for logout", e);
        }
    }

    /**
     * Log a token refresh event
     */
    public void logRefresh(UUID userId, String username, String ip, String deviceId) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.REFRESH_TOKEN);
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setDeviceId(deviceId);
            auditLog.setSuccess(true);

            auditLogRepository.save(auditLog);

            log.info("audit_refresh",
                    kv("userId", userId),
                    kv("username", username),
                    kv("ip", ip),
                    kv("deviceId", deviceId)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for refresh", e);
        }
    }

    /**
     * Log a user signup event
     */
    public void logSignup(UUID userId, String username, String ip) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.SIGNUP);
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setSuccess(true);

            auditLogRepository.save(auditLog);

            log.info("audit_signup",
                    kv("userId", userId),
                    kv("username", username),
                    kv("ip", ip)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for signup", e);
        }
    }

    /**
     * Log a key rotation event
     */
    public void logKeyRotation(UUID adminUserId, String adminUsername) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.KEY_ROTATION);
            auditLog.setUserId(adminUserId);
            auditLog.setUsername(adminUsername);
            auditLog.setSuccess(true);

            auditLogRepository.save(auditLog);

            log.info("audit_key_rotation",
                    kv("adminUserId", adminUserId),
                    kv("adminUsername", adminUsername)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for key rotation", e);
        }
    }

    /**
     * Log an admin creation event
     */
    public void logAdminCreation(UUID creatorUserId, String creatorUsername, String newAdminUsername, String ip) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditEvent.ADMIN_CREATION);
            auditLog.setUserId(creatorUserId);
            auditLog.setUsername(creatorUsername != null ? creatorUsername : "system");
            auditLog.setIpAddress(ip);
            auditLog.setSuccess(true);
            auditLog.setMetadata("new_admin_username=" + newAdminUsername);

            auditLogRepository.save(auditLog);

            log.info("audit_admin_creation",
                    kv("creatorUserId", creatorUserId),
                    kv("creatorUsername", creatorUsername),
                    kv("newAdminUsername", newAdminUsername),
                    kv("ip", ip)
            );
        } catch (Exception e) {
            log.error("Failed to save audit log for admin creation", e);
        }
    }
}
