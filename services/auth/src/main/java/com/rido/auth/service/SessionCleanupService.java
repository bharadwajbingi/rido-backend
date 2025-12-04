package com.rido.auth.service;

import com.rido.auth.repo.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SessionCleanupService {

    private final RefreshTokenRepository repo;

    public SessionCleanupService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    // Run cleanup every 6 hours (bulk delete)
    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanup() {
        repo.deleteExpiredOrRevoked(Instant.now());
    }
}
