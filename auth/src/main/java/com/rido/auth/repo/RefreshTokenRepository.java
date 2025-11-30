package com.rido.auth.repo;
import java.util.List;

import com.rido.auth.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Query(value = "SELECT NOW()", nativeQuery = true)
    Instant getDatabaseTime();

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId")
    void revokeAllForUser(@Param("userId") UUID userId);
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(UUID userId);

}
