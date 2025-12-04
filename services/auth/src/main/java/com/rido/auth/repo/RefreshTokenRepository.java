package com.rido.auth.repo;

import com.rido.auth.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    // ============================================================
    // FIND BY HASH (used in refresh flow)
    // ============================================================
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    // ============================================================
    // DATABASE CLOCK (for rotation consistency)
    // ============================================================
    @Query(value = "SELECT NOW()", nativeQuery = true)
    Instant getDatabaseTime();

    // ============================================================
    // LIST ALL SESSIONS FOR USER — newest → oldest
    // ============================================================
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    List<RefreshTokenEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    // ============================================================
    // ACTIVE (NON-REVOKED) SESSIONS
    // ============================================================
    @Query("SELECT r FROM RefreshTokenEntity r " +
            "WHERE r.userId = :userId AND r.revoked = false " +
            "ORDER BY r.createdAt DESC")
    List<RefreshTokenEntity> findActiveByUserId(@Param("userId") UUID userId);

    // ============================================================
    // FIND OLDEST ACTIVE SESSIONS (for limit enforcement)
    // ============================================================
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.userId = :userId AND r.revoked = false ORDER BY r.createdAt ASC")
    List<RefreshTokenEntity> findActiveByUserIdOrderByCreatedAtAsc(@Param("userId") UUID userId);

    // ============================================================
    // SIMPLE ACTIVE CHECK
    // ============================================================
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(UUID userId);

    // ============================================================
    // REVOKE ONE SESSION ONLY (FIXED)
    // ============================================================
    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.id = :id")
    void revokeOne(@Param("id") UUID id);

    // ============================================================
    // REVOKE ALL SESSIONS FOR USER
    // ============================================================
    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId")
    void revokeAllForUser(@Param("userId") UUID userId);


    // ============================================================
// CLEANUP: BULK DELETE EXPIRED + REVOKED (SAFE)
// ============================================================
@Modifying
@Transactional
@Query("DELETE FROM RefreshTokenEntity r WHERE r.revoked = true OR r.expiresAt < :now")
void deleteExpiredOrRevoked(@Param("now") Instant now);


    // ============================================================
    // ACTIVE TOKEN COUNT (Prometheus gauge)
    // ============================================================
    long countByRevokedFalse();

}
