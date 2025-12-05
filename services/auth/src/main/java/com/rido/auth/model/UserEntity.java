package com.rido.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    // Auth roles: USER or ADMIN only
    @Column(nullable = false)
    private String role = "USER";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ------------------------------
    // Login security fields
    // ------------------------------
    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ------------------------------
    // Getters & Setters
    // ------------------------------
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
