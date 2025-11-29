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

    public LoginAttemptService(StringRedisTemplate redis,
                               UserRepository userRepository) {
        this.redis = redis;
        this.userRepository = userRepository;
    }

    public void ensureNotLocked(String username) {
        String lockedKey = "auth:login:locked:" + username;

        String flag = redis.opsForValue().get(lockedKey);
        if (flag != null) {
            throw new AccountLockedException("Account locked due to too many failed attempts.");
        }
    }

    public void onFailure(String username, String ip, UserEntity user) {

        String attemptsKey = "auth:login:attempts:" + username + ":" + ip;

        Long attempts = redis.opsForValue().increment(attemptsKey);

        if (attempts == 1) {
            redis.expire(attemptsKey, Duration.ofMinutes(15));
        }

        if (attempts > 5) {
            redis.opsForValue().set("auth:login:locked:" + username, "1",
                    Duration.ofMinutes(30));

            if (user != null) {
                user.setLockedUntil(Instant.now().plusSeconds(30 * 60));
                userRepository.save(user);
            }

            throw new AccountLockedException("Too many failed attempts. Locked 30 minutes.");
        }
    }

    public void onSuccess(String username, String ip, UserEntity user) {
        redis.delete("auth:login:attempts:" + username + ":" + ip);
        redis.delete("auth:login:locked:" + username);

        user.setLockedUntil(null);
        userRepository.save(user);
    }
     // 🔥 TEST-ONLY UNLOCK (correct)
    public void resetFailures(String username) {

        // 1) Delete ALL possible attempts keys (IP varies)
        // pattern delete
        redis.keys("auth:login:attempts:" + username + ":*")
                .forEach(redis::delete);

        // 2) Delete lock key
        redis.delete("auth:login:locked:" + username);

        // 3) Reset DB lock
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setLockedUntil(null);
            userRepository.save(u);
        });
    }

}
