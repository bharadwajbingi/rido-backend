package com.rido.auth.controller;

import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.rido.auth.service.AuditLogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/internal")  // MUST NOT be exposed publicly in gateway
public class InternalAdminController {

    private static final Logger log = LoggerFactory.getLogger(InternalAdminController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String setupKey;
    private final AuditLogService auditLogService;

    public InternalAdminController(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   AuditLogService auditLogService,
                                   Environment env) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.setupKey = env.getProperty("ADMIN_SETUP_KEY"); // secret injected via infra
        this.auditLogService = auditLogService;
    }

    @PostMapping("/admin/create")
    public ResponseEntity<?> createAdmin(
            @RequestHeader(value = "X-SYSTEM-KEY", required = false) String key,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        // --------------------------------------------------------------------------------------
        // Validate internal key
        // --------------------------------------------------------------------------------------
        if (setupKey == null || !setupKey.equals(key)) {
            log.warn("internal_admin_create_forbidden", Map.of("reason", "bad_key"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden"));
        }

        // --------------------------------------------------------------------------------------
        // Validate required fields
        // --------------------------------------------------------------------------------------
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "missing_fields"));
        }

        // --------------------------------------------------------------------------------------
        // Check duplicates
        // --------------------------------------------------------------------------------------
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "exists"));
        }

        // --------------------------------------------------------------------------------------
        // Create admin user
        // --------------------------------------------------------------------------------------
        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        admin.setCreatedAt(Instant.now());

        userRepository.save(admin);

        log.info("internal_admin_created", Map.of("username", username));

        // Audit logging
        String ip = request.getRemoteAddr();
        auditLogService.logAdminCreation(null, "system", username, ip);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
