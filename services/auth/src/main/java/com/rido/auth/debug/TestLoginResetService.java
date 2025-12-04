package com.rido.auth.debug;

import com.rido.auth.repo.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Profile({"dev", "test"})   // ACTIVE ONLY IN DEV + TEST
@Service
public class TestLoginResetService {

    private final StringRedisTemplate redis;
    private final UserRepository repo;

    public TestLoginResetService(StringRedisTemplate redis, UserRepository repo) {
        this.redis = redis;
        this.repo = repo;
    }

    public void resetFailures(String username) {

        // Delete attempt counter (fixed key pattern to match LoginAttemptService)
        redis.delete("auth:login:attempts:" + username);

        // Delete lock flag
        redis.delete("auth:login:locked:" + username);

        // Clear DB lock
        repo.findByUsername(username).ifPresent(u -> {
            u.setLockedUntil(null);
            repo.save(u);
        });
    }
}
