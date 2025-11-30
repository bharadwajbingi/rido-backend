package com.rido.auth.repo;

import com.rido.auth.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
    long countByLockedUntilAfter(Instant instant);

}

// [otel.javaagent] Initializing OpenTelemetry Java Agent
