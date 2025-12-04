package com.rido.auth.service;

import com.rido.auth.exception.UsernameAlreadyExistsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserRegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public UserEntity register(String username, String password, String ip) {
        log.info("auth_register_attempt", kv("username", username));

        userRepository.findByUsername(username)
                .ifPresent(u -> {
                    log.warn("auth_register_failed",
                            kv("username", username),
                            kv("reason", "username_taken"));
                    throw new UsernameAlreadyExistsException("Username already exists");
                });

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);

        log.info("auth_register_success", kv("username", username));

        // Audit logging
        auditLogService.logSignup(user.getId(), username, ip);

        return user;
    }

    public UserEntity createAdmin(String username, String password, String ip, String creatorUsername) {
        log.info("auth_admin_create_attempt", kv("username", username), kv("creator", creatorUsername));

        userRepository.findByUsername(username)
                .ifPresent(u -> {
                    throw new UsernameAlreadyExistsException("Username already exists");
                });

        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");

        userRepository.save(admin);

        log.info("auth_admin_create_success", kv("username", username), kv("creator", creatorUsername));

        // Audit logging
        auditLogService.logAdminCreation(null, creatorUsername, username, ip);

        return admin;
    }
}
