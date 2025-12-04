package com.rido.auth.repo;

import com.rido.auth.model.AuditEvent;
import com.rido.auth.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for audit log persistence and querying.
 * Provides methods to retrieve audit logs by various criteria.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find all audit logs for a specific user
     */
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find all audit logs of a specific event type
     */
    Page<AuditLog> findByEventType(AuditEvent eventType, Pageable pageable);

    /**
     * Find all audit logs within a time range
     */
    Page<AuditLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    /**
     * Find all audit logs for a specific username
     */
    Page<AuditLog> findByUsername(String username, Pageable pageable);

    /**
     * Find all failed audit events
     */
    Page<AuditLog> findBySuccessFalse(Pageable pageable);
}
