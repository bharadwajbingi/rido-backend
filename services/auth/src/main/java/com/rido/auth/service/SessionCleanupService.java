package com.rido.auth.service;

import com.rido.auth.repo.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SessionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final RefreshTokenRepository repo;
    
    @Value("${auth.cleanup.batch-size:1000}")
    private int batchSize;

    public SessionCleanupService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /**
     * Run cleanup every 6 hours
     * Deletes expired/revoked sessions in batches to avoid table locks
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanup() {
        log.info("Starting scheduled session cleanup (batch size: {})...", batchSize);
        
        long startTime = System.currentTimeMillis();
        int totalDeleted = 0;
        int batchCount = 0;
        
        try {
            int deleted;
            do {
                // Delete in batches to prevent table locks
                deleted = repo.deleteExpiredOrRevokedBatch(Instant.now(), batchSize);
                totalDeleted += deleted;
                batchCount++;
                
                if (deleted > 0) {
                    log.debug("Cleanup batch {} deleted {} sessions", batchCount, deleted);
                }
                
                // Small delay between batches to reduce database load
                if (deleted == batchSize) {
                    Thread.sleep(100); // 100ms delay
                }
                
            } while (deleted == batchSize); // Continue while batches are full
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Session cleanup completed: {} sessions deleted in {} batches ({}ms)",
                    totalDeleted, batchCount, duration);
                    
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Session cleanup interrupted", e);
        } catch (Exception e) {
            log.error("Session cleanup failed after deleting {} sessions", totalDeleted, e);
        }
    }
}
