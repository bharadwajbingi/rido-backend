package com.rido.auth.service;

import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.model.UserEntity;
import com.rido.auth.repo.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    public LoginAttemptService(StringRedisTemplate redis,
                               UserRepository userRepository) {
        this.redis = redis;
        this.userRepository = userRepository;
    }

    // 🚫 CHECK BEFORE PASSWORD VALIDATION
    public void ensureNotLocked(String username) {

        String lockedKey = "auth:login:locked:" + username;
        String redisLock = redis.opsForValue().get(lockedKey);

        // 1️⃣ If Redis says locked → locked
        if (redisLock != null) {
            throw new AccountLockedException("Account locked due to too many failed attempts.");
        }

        // 2️⃣ Check DB lock (hard lock)
        userRepository.findByUsername(username).ifPresent(user -> {
            Instant lockedUntil = user.getLockedUntil();

            if (lockedUntil != null) {
                // EXPIRED → auto-unlock
                if (lockedUntil.isBefore(Instant.now())) {
                    user.setLockedUntil(null);
                    userRepository.save(user);
                } else {
                    // STILL ACTIVE → block login
                    throw new AccountLockedException("Account locked until: " + lockedUntil);
                }
            }
        });
    }

    // ❌ FAILURE HANDLING
    public void onFailure(String username, String ip, UserEntity user) {

        String attemptsKey = "auth:login:attempts:" + username + ":" + ip;

        Long attempts = redis.opsForValue().increment(attemptsKey);

        if (attempts == 1) {
            redis.expire(attemptsKey, ATTEMPT_TTL);
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {

            // Redis lock
            redis.opsForValue().set("auth:login:locked:" + username, "1", LOCK_DURATION);

            // DB lock
            if (user != null) {
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
                userRepository.save(user);
            }

            throw new AccountLockedException("Account locked due to too many failed attempts.");
        }
    }

    // ✅ CLEAR LOCK ON SUCCESSFUL LOGIN
    public void onSuccess(String username, String ip, UserEntity user) {
        redis.delete("auth:login:attempts:" + username + ":" + ip);
        redis.delete("auth:login:locked:" + username);

        user.setLockedUntil(null);
        userRepository.save(user);
    }

    // 🧪 TEST UTILITY
    public void resetFailures(String username) {

        redis.keys("auth:login:attempts:" + username + ":*")
                .forEach(redis::delete);

        redis.delete("auth:login:locked:" + username);

        userRepository.findByUsername(username).ifPresent(u -> {
            u.setLockedUntil(null);
            userRepository.save(u);
        });
    }
}
