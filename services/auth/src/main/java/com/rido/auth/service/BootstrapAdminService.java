package com.rido.auth.service;

import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bootstrap service that creates the initial admin user on startup.
 * Reads credentials from environment variables and creates admin if none exists.
 */
@Service
public class BootstrapAdminService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Value("${admin.bootstrap.username:admin}")
    private String bootstrapUsername;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    public BootstrapAdminService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    @Transactional
    public void bootstrapAdmin() {
        // Skip if no password configured
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("bootstrap_admin_skipped: No FIRST_ADMIN_PASSWORD configured");
            return;
        }

        // Check if any admin already exists
        boolean adminExists = userRepository.existsByRole("ADMIN");
        if (adminExists) {
            log.info("bootstrap_admin_skipped: Admin user already exists");
            return;
        }

        // Check if username is taken (edge case: user account with admin username)
        if (userRepository.findByUsername(bootstrapUsername).isPresent()) {
            log.warn("bootstrap_admin_conflict: Username '{}' already exists but is not an admin", bootstrapUsername);
            return;
        }

        // Create the bootstrap admin
        UserEntity admin = new UserEntity();
        admin.setUsername(bootstrapUsername);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setRole("ADMIN");
        admin.setCreatedAt(Instant.now());

        userRepository.save(admin);

        // Audit log
        auditLogService.logAdminCreation(null, "system", bootstrapUsername, "127.0.0.1");

        log.info("bootstrap_admin_created: Created bootstrap admin user '{}'", bootstrapUsername);
    }
}
