package com.rido.auth.service;

import com.rido.auth.repo.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    private RefreshTokenRepository repo;

    private SessionCleanupService sessionCleanupService;

    private static final int BATCH_SIZE = 100;

    @BeforeEach
    void setUp() throws Exception {
        sessionCleanupService = new SessionCleanupService(repo);
        
        Field batchSizeField = SessionCleanupService.class.getDeclaredField("batchSize");
        batchSizeField.setAccessible(true);
        batchSizeField.set(sessionCleanupService, BATCH_SIZE);
    }

    @Nested
    @DisplayName("Cleanup Batch Tests")
    class CleanupBatch {

        @Test
        @DisplayName("Should delete expired sessions in batches")
        void shouldDeleteExpiredSessions_inBatches() {
            when(repo.deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE)))
                    .thenReturn(50);

            sessionCleanupService.cleanup();

            verify(repo).deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("Should continue while batch is full")
        void shouldContinue_whileBatchIsFull() {
            when(repo.deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE)))
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(50);

            sessionCleanupService.cleanup();

            verify(repo, times(3)).deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("Should stop when batch is not full")
        void shouldStop_whenBatchNotFull() {
            when(repo.deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE)))
                    .thenReturn(50);

            sessionCleanupService.cleanup();

            verify(repo, times(1)).deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("Should stop when no sessions to delete")
        void shouldStop_whenNoSessionsToDelete() {
            when(repo.deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE)))
                    .thenReturn(0);

            sessionCleanupService.cleanup();

            verify(repo, times(1)).deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleException_gracefully() {
            when(repo.deleteExpiredOrRevokedBatch(any(Instant.class), eq(BATCH_SIZE)))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatCode(() -> sessionCleanupService.cleanup())
                    .doesNotThrowAnyException();
        }
    }
}
