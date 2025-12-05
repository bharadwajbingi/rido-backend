package com.rido.auth.service;

import com.rido.auth.dto.TokenResponse;
import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class LoginService {

    /**
     * Dummy password hash for timing attack mitigation.
     * Used to perform fake password verification when user doesn't exist.
     * This ensures constant-time response regardless of whether the user exists.
     */
    private static final String DUMMY_PASSWORD_HASH = "$argon2id$v=19$m=4096,t=3,p=1$kRp/g995IaBi1gf17tcLFQ$1wfk5V0lsRbZBcYuHZnZjw6/Vna/QKOsiRyxjvmvku4";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenService tokenService;
    private final TracingService tracingService;
    private final AuditLogService auditLogService;

    // Micrometer
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter loginLockoutCounter;
    private final Timer requestTimer;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            TokenService tokenService,
            TracingService tracingService,
            AuditLogService auditLogService,
            MeterRegistry registry
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenService = tokenService;
        this.tracingService = tracingService;
        this.auditLogService = auditLogService;

        this.loginSuccessCounter = registry.counter("auth.login.success");
        this.loginFailureCounter = registry.counter("auth.login.failure");
        this.loginLockoutCounter = registry.counter("auth.login.lockout");
        this.requestTimer = registry.timer("auth.request.duration");
    }

    public TokenResponse login(String username, String password, String deviceId, String ip, String userAgent) {

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);

        // 1. Check locks (SKIP FOR ADMINS)
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            if (!"ADMIN".equals(user.getRole())) {
                loginAttemptService.ensureNotLocked(username);
            }
        } else {
            // Unknown user -> enforce lock check to prevent DoS on non-existent accounts
            loginAttemptService.ensureNotLocked(username);
        }

        // 2. TIMING ATTACK MITIGATION: Always perform password verification
        // This ensures constant-time response whether user exists or not
        boolean passwordMatches;
        UserEntity user;
        
        if (userOpt.isEmpty()) {
            // Fake user flow: verify against dummy hash to match timing
            // This prevents username enumeration via timing attacks
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            passwordMatches = false;
            user = null;
            
            // Log failure with uniform error message
            loginFailureCounter.increment();
            loginAttemptService.onFailure(username, ip, null);
            auditLogService.logLoginFailed(username, ip, deviceId, userAgent, "Invalid credentials");
            throw new InvalidCredentialsException("Invalid username or password");
        }
        
        user = userOpt.get();

        // 3. Check DB Lock (SKIP FOR ADMINS)
        if (!"ADMIN".equals(user.getRole()) && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            loginFailureCounter.increment();
            loginLockoutCounter.increment();
            auditLogService.logLoginFailed(username, ip, deviceId, userAgent, "Account locked");
            throw new AccountLockedException("Account locked");
        }

        // 4. Verify password (actual user)
        passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        
        if (!passwordMatches) {
            loginFailureCounter.increment();
            loginAttemptService.onFailure(username, ip, user);
            auditLogService.logLoginFailed(username, ip, deviceId, userAgent, "Invalid credentials");
            // Uniform error message - no indication of whether user exists
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // 5. Login successful
        loginAttemptService.onSuccess(username, ip, user);
        loginSuccessCounter.increment();

        auditLogService.logLoginSuccess(user.getId(), username, ip, deviceId, userAgent);

        tracingService.tagCurrentSpan(user.getId(), deviceId, ip, null);

        return requestTimer.record(() ->
                tokenService.createTokens(user.getId(), user.getRole(), deviceId, ip, userAgent)
        );
    }
}
