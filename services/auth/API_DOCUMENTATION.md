# Auth Service API Documentation

## Overview
The Auth Service handles user registration, authentication, session management, and token issuance. It uses **JWT (JSON Web Tokens)** for securing endpoints.

**Base URL**: `/auth` (relative to the gateway or service root)

## Authentication
Most endpoints require authentication via a Bearer Token.
- **Header**: `Authorization: Bearer <access_token>`
- **Internal Headers**: Some endpoints rely on `X-User-ID` which is typically populated by the API Gateway after validating the token.

## Endpoints

### 1. Register User
Creates a new user account.

- **URL**: `/register`
- **Method**: `POST`
- **Auth Required**: No

**Request Body** (`RegisterRequest`):
```json
{
  "username": "user123",
  "password": "securePassword123"
}
```
- `username`: 3-30 characters.
- `password`: Min 6 characters.

**Response**:
- `200 OK`: `{"status": "ok"}`
- `400 Bad Request`: Validation errors.

---

### 2. Login
Authenticates a user and returns access/refresh tokens.

- **URL**: `/login`
- **Method**: `POST`
- **Auth Required**: No
- **Headers**:
    - `X-Device-Id` (Optional): Device identifier.
    - `User-Agent` (Optional): Browser/Client info.

**Request Body** (`LoginRequest`):
```json
{
  "username": "user123",
  "password": "securePassword123",
  "deviceId": "device-uuid", // Optional
  "ip": "127.0.0.1",         // Optional
  "userAgent": "Mozilla/5.0" // Optional
}
```

**Response** (`TokenResponse`):
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "dcc3...",
  "expiresIn": 3600
}
```

---

### 3. Refresh Token
Obtains a new access token using a valid refresh token.

- **URL**: `/refresh`
- **Method**: `POST`
- **Auth Required**: No

**Request Body** (`RefreshRequest`):
```json
{
  "refreshToken": "dcc3..."
}
```

**Response** (`TokenResponse`):
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "new-refresh-token...",
  "expiresIn": 3600
}
```

---

### 4. Logout
Invalidates the refresh token.

- **URL**: `/logout`
- **Method**: `POST`
- **Auth Required**: Yes (Optional but recommended to pass Authorization header)

**Request Body** (`LogoutRequest`):
```json
{
  "refreshToken": "dcc3..."
}
```

**Response**:
- `200 OK`: `{"status": "ok"}`

---

### 5. Get Current User
Retrieves details of the currently authenticated user.

- **URL**: `/me`
- **Method**: `GET`
- **Auth Required**: Yes (Requires `X-User-ID` header, typically injected by Gateway)

**Response**:
```json
{
  "id": "uuid-string",
  "username": "user123"
}
```
- `404 Not Found`: If user does not exist.

---

### 6. Check Username Availability
Checks if a username is already taken.

- **URL**: `/check-username`
- **Method**: `GET`
- **Query Params**: `username`

**Response**:
```json
{
  "available": true
}
```

---

### 7. List Active Sessions
Lists all active sessions for the current user.

- **URL**: `/sessions`
- **Method**: `GET`
- **Auth Required**: Yes (Requires `X-User-ID` header)

**Response** (Array of `SessionDTO`):
```json
[
  {
    "id": "session-uuid",
    "deviceId": "device-uuid",
    "ip": "127.0.0.1",
    "userAgent": "Mozilla/5.0...",
    "revoked": false,
    "createdAt": "2023-10-27T10:00:00Z",
    "expiresAt": "2023-11-27T10:00:00Z"
  }
]
```

---

### 8. Revoke All Sessions
Revokes all active sessions for the user (Global Logout).

- **URL**: `/sessions/revoke-all`
- **Method**: `POST`
- **Auth Required**: Yes (Requires `X-User-ID` header)

**Response**:
- `200 OK`: `{"status": "ok"}`

---

### 9. Revoke Specific Session
Revokes a single session by ID.

- **URL**: `/sessions/{sessionId}/revoke`
- **Method**: `POST`
- **Auth Required**: Yes (Requires `X-User-ID` header)

**Response**:
- `200 OK`: `{"status": "revoked"}`

---

### 10. Public JWKS
Exposes the JSON Web Key Set for verifying JWT signatures.

- **URL**: `/keys/.well-known/jwks.json` (or `/keys/jwks.json`)
- **Method**: `GET`
- **Auth Required**: No

**Response**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "kid": "key-id",
      "n": "..."
    }
  ]
}
```

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

## Data Models

### RegisterRequest
| Field | Type | Required | Description |
|---|---|---|---|
| `username` | String | Yes | 3-30 chars |
| `password` | String | Yes | Min 6 chars |

### LoginRequest
| Field | Type | Required | Description |
|---|---|---|---|
| `username` | String | Yes | |
| `password` | String | Yes | |
| `deviceId` | String | No | For session tracking |
| `ip` | String | No | |
| `userAgent` | String | No | |

### TokenResponse
| Field | Type | Description |
|---|---|---|
| `accessToken` | String | JWT for API access |
| `refreshToken` | String | Token to refresh access |
| `expiresIn` | Long | Seconds until access token expires |
