package com.rido.auth.service;

import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.kv;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;

    // METRICS
    private final Counter loginAttemptCounter;
    private final Counter loginBlockedCounter;
    private final Counter loginHardLockCounter;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    public LoginAttemptService(
            StringRedisTemplate redis,
            UserRepository userRepository,
            MeterRegistry registry
    ) {
        this.redis = redis;
        this.userRepository = userRepository;

        this.loginAttemptCounter = registry.counter("auth.login.attempt");
        this.loginBlockedCounter = registry.counter("auth.login.blocked");
        this.loginHardLockCounter = registry.counter("auth.login.hardlock");
    }

    // 🚫 CHECK BEFORE PASSWORD VALIDATION
    public void ensureNotLocked(String username) {

        String lockedKey = "auth:login:locked:" + username;
        String redisLock = redis.opsForValue().get(lockedKey);

        if (redisLock != null) {
            loginBlockedCounter.increment();

            logger.warn("login_blocked_redis",
                    kv("username", username),
                    kv("reason", "redis_lock"));

            throw new AccountLockedException("Account locked due to too many failed attempts.");
        }

        // Check DB lock
        userRepository.findByUsername(username).ifPresent(user -> {
            Instant lockedUntil = user.getLockedUntil();

            if (lockedUntil != null) {

                if (lockedUntil.isBefore(Instant.now())) {

                    user.setLockedUntil(null);
                    userRepository.save(user);

                    logger.info("lock_auto_cleared",
                            kv("username", username),
                            kv("unlocked", true));
                } else {

                    loginHardLockCounter.increment();

                    logger.warn("login_blocked_db",
                            kv("username", username),
                            kv("locked_until", lockedUntil),
                            kv("reason", "still_locked"));

                    throw new AccountLockedException("Account locked until: " + lockedUntil);
                }
            }
        });
    }

    // ❌ FAILURE HANDLING
    public void onFailure(String username, String ip, UserEntity user) {

        loginAttemptCounter.increment();

        // ⭐ USERNAME-BASED RATE LIMITING (prevents brute force on single account)
        String attemptsKey = "auth:login:attempts:" + username;
        Long attempts = redis.opsForValue().increment(attemptsKey);

        if (attempts == 1) {
            redis.expire(attemptsKey, ATTEMPT_TTL);
        }

        // ⭐ IP-BASED RATE LIMITING (prevents distributed attacks)
        String ipAttemptsKey = "auth:login:ip:attempts:" + ip;
        Long ipAttempts = redis.opsForValue().increment(ipAttemptsKey);

        if (ipAttempts == 1) {
            redis.expire(ipAttemptsKey, ATTEMPT_TTL);
        }

        logger.info("login_failure",
                kv("username", username),
                kv("ip", ip),
                kv("attempts", attempts),
                 kv("ipAttempts", ipAttempts));

        // Check IP-based limit (20 failures from same IP)
        if (ipAttempts != null && ipAttempts > 20) {
            loginBlockedCounter.increment();
            logger.warn("ip_rate_limit_exceeded",
                    kv("ip", ip),
                    kv("attempts", ipAttempts),
                    kv("threshold", 20));
            throw new AccountLockedException("Too many failed login attempts from this IP. Try again later.");
        }

        // Check username-based limit (5 failures per username)
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            
            // ⭐ SKIP LOCKING FOR ADMINS
            if (user != null && "ADMIN".equals(user.getRole())) {
                logger.warn("admin_login_failure_threshold_exceeded", 
                        kv("username", username), 
                        kv("attempts", attempts),
                        kv("action", "skip_lock"));
                return;
            }

            redis.opsForValue().set("auth:login:locked:" + username, "1", LOCK_DURATION);

            if (user != null) {
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
                userRepository.save(user);
            }

            loginBlockedCounter.increment();

            logger.warn("account_locked",
                    kv("username", username),
                    kv("ip", ip),
                    kv("reason", "too_many_attempts"));

            throw new AccountLockedException("Account locked due to too many failed attempts.");
        }
    }

    // ✔ CLEAR LOCK ON SUCCESSFUL LOGIN
    public void onSuccess(String username, String ip, UserEntity user) {

        redis.delete("auth:login:attempts:" + username);
        redis.delete("auth:login:locked:" + username);

        if (user != null) {
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        logger.info("login_success_reset",
                kv("username", username),
                kv("ip", ip));
    }

    // 🧪 TEST UTILITY
    public void resetFailures(String username) {

        redis.delete("auth:login:attempts:" + username);
        redis.delete("auth:login:locked:" + username);

        userRepository.findByUsername(username).ifPresent(u -> {
            u.setLockedUntil(null);
            userRepository.save(u);
        });

        logger.info("login_reset_test",
                kv("username", username));
    }
}
