package com.rido.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 256)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip")
    private String ip;

    @Column
    private UUID jti;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Getters & setters

    public UUID getId() {
        return id;
    }
    // No setter for id → JPA manages it

    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }

    public UUID getJti() {
        return jti;
    }
    public void setJti(UUID jti) {
        this.jti = jti;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
