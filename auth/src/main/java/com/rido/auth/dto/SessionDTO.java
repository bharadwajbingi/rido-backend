package com.rido.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionDTO(
        UUID id,
        String deviceId,
        String ip,
        String userAgent,
        boolean revoked,
        Instant createdAt,
        Instant expiresAt
) {}
