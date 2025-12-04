# Auth Service System Architecture

## Overview
This document outlines the internal architecture, security mechanisms, and administrative interfaces of the Auth Service. It is intended for backend engineers and DevOps personnel.

## Security Features

### 1. Rate Limiting
To prevent abuse, the following rate limits are enforced (using a sliding window algorithm via Redis):
- **Registration**: 10 requests / 60 seconds (per IP).
- **Login**: 
    - Global: 50 requests / 60 seconds (per IP).
    - Per User: 10 failed attempts / 5 minutes.
- **Refresh Token**: 20 requests / 60 seconds (per IP).

*Exceeding these limits results in `429 Too Many Requests`.*

### 2. Account Lockout
Protects against brute-force attacks.
- **Trigger**: 5 consecutive failed login attempts.
- **Duration**: 30 minutes.
- **Scope**: Locks the account by username (persisted in Redis & DB).
- **Response**: `401 Unauthorized` with specific error message (or generic to prevent enumeration, though internal logs show "Account locked").

### 3. mTLS (Mutual TLS)
Used for Service-to-Service authentication.
- **Mechanism**: The service extracts the Client Certificate from the request.
- **Identity**: The Common Name (CN) from the certificate Subject DN is used to identify the calling service.
- **Header**: Sets `X-Service-Name` internally for authorization policies.

### 4. Key Rotation
JWT signing keys are managed securely via HashiCorp Vault.
- **Storage**: Keys (Private/Public) are stored in Vault at `secret/data/auth/keys`.
- **Rotation**: 
    - Automatic generation on startup if missing.
    - Manual rotation via Admin API (`/auth/keys/rotate`).
- **Verification**: Public keys are exposed via JWKS for clients/gateways to verify signatures.

### 5. Replay Protection & Token Security
- **Refresh Token Rotation**: Refresh tokens are one-time use. Using a refresh token invalidates it and issues a new one.
- **Device Binding**: Refresh tokens are bound to `DeviceId` and `User-Agent`. Mismatches trigger `DeviceMismatchException` and security alerts.
- **Short-lived Access Tokens**: Default TTL is 5 minutes (configurable via `JWT_ACCESS_TTL`), minimizing the window for replay attacks if a token is leaked.

## Internal Admin Endpoints
> [!WARNING]
> These endpoints are for internal use only and should NOT be exposed via the public gateway.

### Create Admin
- **URL**: `/internal/admin/create`
- **Method**: `POST`
- **Headers**: `X-SYSTEM-KEY` (Must match server config)
- **Body**: `{"username": "admin", "password": "..."}`

### Rotate Keys
- **URL**: `/auth/keys/rotate`
- **Method**: `POST`
- **Auth Required**: Yes (Requires `ROLE_ADMIN`)
- **Description**: Rotates the RSA key pair used for signing JWTs and persists the new key to Vault.

## Infrastructure Dependencies

### Redis
- **Usage**: 
    - Rate limiting counters (sliding window).
    - Login failure tracking (attempts/lockout).
    - Session storage (optional/caching).
- **Key Patterns**:
    - `rate:*`: Rate limit windows.
    - `auth:login:attempts:*`: Failed login counters.
    - `auth:login:locked:*`: Account lockout flags.

### HashiCorp Vault
- **Usage**: 
    - Storage of JWT signing keys (Private/Public).
    - Database credentials (dynamic injection).
- **Path**: `secret/data/auth/keys`

### PostgreSQL
- **Usage**:
    - User accounts (`users` table).
    - Refresh tokens (`refresh_tokens` table).
    - Audit logs (if persisted to DB).
