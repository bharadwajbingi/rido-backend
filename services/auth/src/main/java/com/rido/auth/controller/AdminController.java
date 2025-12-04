package com.rido.auth.controller;

import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.AuditLog;
import com.rido.auth.repo.AuditLogRepository;
import com.rido.auth.service.AdminLoginService;
import com.rido.auth.service.AuditLogService;
import com.rido.auth.service.UserRegistrationService;
import com.rido.auth.util.IpExtractorService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin controller serving all admin endpoints on port 9090.
 * These endpoints are NOT exposed through the gateway.
 * Access only via VPN/SSH tunnel to the internal network.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminLoginService adminLoginService;
    private final UserRegistrationService userRegistrationService;
    private final JwtKeyStore keyStore;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final IpExtractorService ipExtractor;

    public AdminController(
            AdminLoginService adminLoginService,
            UserRegistrationService userRegistrationService,
            JwtKeyStore keyStore,
            AuditLogService auditLogService,
            AuditLogRepository auditLogRepository,
            IpExtractorService ipExtractor
    ) {
        this.adminLoginService = adminLoginService;
        this.userRegistrationService = userRegistrationService;
        this.keyStore = keyStore;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.ipExtractor = ipExtractor;
    }

    // =====================================================
    // HEALTH CHECK (public)
    // =====================================================
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "port", 9090));
    }

    // =====================================================
    // ADMIN LOGIN (public on admin port)
    // =====================================================
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        String ip = ipExtractor.extractClientIp(request); // Secure IP extraction (admin port)
        String userAgent = request.getHeader("User-Agent");

        TokenResponse tokens = adminLoginService.login(username, password, ip, userAgent);

        return ResponseEntity.ok(tokens);
    }

    // =====================================================
    // CREATE NEW ADMIN (requires admin JWT)
    // =====================================================
    @PostMapping("/create")
    public ResponseEntity<?> createAdmin(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        // Get creator info from request (set by AdminAuthenticationFilter)
        String creatorId = (String) request.getAttribute("userId");
        String ip = ipExtractor.extractClientIp(request); // Secure IP extraction (admin port)

        log.info("admin_create_request: Creator {} creating admin {}", creatorId, username);

        userRegistrationService.createAdmin(username, password, ip, creatorId);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Admin created successfully",
                "username", username
        ));
    }

    // =====================================================
    // ROTATE JWT SIGNING KEY (requires admin JWT)
    // =====================================================
    @PostMapping("/key/rotate")
    public ResponseEntity<?> rotateKey(HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        
        log.info("key_rotation_request: Admin {} rotating JWT signing key", adminId);

        keyStore.rotate();

        // Audit log
        if (adminId != null) {
            auditLogService.logKeyRotation(UUID.fromString(adminId), adminId);
        }

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "newKid", keyStore.getCurrentKid()
        ));
    }

    // =====================================================
    // GET AUDIT LOGS (requires admin JWT)
    // =====================================================
    @GetMapping("/audit/logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (size > 100) size = 100; // Limit max page size

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        List<AuditLog> logs = auditLogRepository.findAll(pageRequest).getContent();

        return ResponseEntity.ok(Map.of(
                "logs", logs,
                "page", page,
                "size", logs.size()
        ));
    }
}
