package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Dedicated login service for admin users.
 * No rate limiting, no lockouts - admins are immune.
 * Only per-IP slow-down is allowed (handled at filter level if needed).
 */
@Service
public class AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditLogService auditLogService;

    private final Counter adminLoginSuccessCounter;
    private final Counter adminLoginFailureCounter;

    public AdminLoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuditLogService auditLogService,
            MeterRegistry registry
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditLogService = auditLogService;

        this.adminLoginSuccessCounter = registry.counter("auth.admin.login.success");
        this.adminLoginFailureCounter = registry.counter("auth.admin.login.failure");
    }

    /**
     * Authenticate an admin user and return JWT tokens.
     * No lockouts, no rate limits applied at this level.
     */
    public TokenResponse login(String username, String password, String ip, String userAgent) {
        log.info("admin_login_attempt", kv("username", username), kv("ip", ip));

        // 1. Find user
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    adminLoginFailureCounter.increment();
                    log.warn("admin_login_failed", kv("username", username), kv("reason", "user_not_found"));
                    return new InvalidCredentialsException("Invalid credentials");
                });

        // 2. Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            adminLoginFailureCounter.increment();
            log.warn("admin_login_failed", kv("username", username), kv("reason", "invalid_password"));
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // 3. Verify role is ADMIN
        if (!"ADMIN".equals(user.getRole())) {
            adminLoginFailureCounter.increment();
            log.warn("admin_login_failed", kv("username", username), kv("reason", "not_an_admin"));
            throw new InvalidCredentialsException("Access denied");
        }

        // 4. Generate tokens
        adminLoginSuccessCounter.increment();
        log.info("admin_login_success", kv("username", username), kv("userId", user.getId()));

        // Audit log for admin login
        auditLogService.logLoginSuccess(user.getId(), username, ip, "admin-console", userAgent);

        return tokenService.createTokens(
                user.getId(),
                user.getRole(),
                "admin-console",  // deviceId for admin logins
                ip,
                userAgent
        );
    }
}
