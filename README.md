# Rido — Real-Time Ride-Hailing Backend

A modern backend inspired by Uber/Ola, built with microservices, WebFlux, Redis, PostgreSQL, Kafka, Docker, and JWT.

## 🏗 Architecture Overview

Rido is designed with five microservices:

### 1. API Gateway (Kotlin)

- Central routing
- **RS256 JWT validation** with JWKS integration
- **Security context propagation** (X-User-Id, X-User-Roles headers)
- JWT audience/issuer validation
- Token blacklist checking (Redis)
- Rate-limiting integration

### 2. Auth Service (Java)

- Signup / login
- **Argon2id password hashing**
- **Refresh token rotation** with replay attack detection
- **RS256 JWT signing** with rotating keys (JWKS)
- Redis brute-force protection
- **Security context extraction** from Gateway headers
- Role-based access control (@PreAuthorize)

### 3. Matching Service (Upcoming)

- Driver assignment
- Redis GEO for proximity
- Kafka for real-time matching

### 4. Trips Service (Upcoming)

- Trip lifecycle
- Event-driven state machine

### 5. Payments Service (Upcoming)

- Mock payments
- Retry logic + DLQ

## 🔧 Tech Stack

### Languages

![Java](https://img.shields.io/badge/Java_21-%23E34F26.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)

### Frameworks

![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring WebFlux](https://img.shields.io/badge/Spring_WebFlux-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring_Cloud_Gateway-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)

### Infrastructure

![Redis](https://img.shields.io/badge/Redis-%23DC382D.svg?style=for-the-badge&logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-%23336791.svg?style=for-the-badge&logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache_Kafka-000000.svg?style=for-the-badge&logo=apache-kafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED.svg?style=for-the-badge&logo=docker&logoColor=white)
![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

## 🔐 Auth Service Features (~85% Complete)

### ✅ Completed

- **Password Security**: Argon2id hashing with strong salt + cost parameters
- **RS256 JWT Tokens**: Asymmetric signing with rotating keys, includes issuer/audience claims, JTI for blacklisting
- **JWKS Endpoint**: Public key distribution for Gateway validation (`/auth/keys/jwks.json`)
- **Refresh Tokens**: Stored as SHA-256 hashes, rotation on every use, replay attack detection
- **Brute-Force Protection**: Redis per-user/IP counter, account lockout after threshold
- **Security Context Filter**: Extracts X-User-Id and X-User-Roles headers, populates Spring Security context
- **Role-Based Access Control**: @PreAuthorize annotations with proper ROLE_ prefix handling
- **Session Management**: List active sessions, revoke individual or all sessions
- **Logout**: Token blacklisting + session revocation
- **Global Error Handling**: Clean JSON error responses with full stack traces for debugging
- **Admin Key Rotation**: Secure endpoint for manual key rotation

### Recent Major Fixes (Latest Commit)

- **JWKS Routing**: Gateway correctly fetches JWKS from Auth service
- **JWT Validation**: Gateway validates RS256 signatures, issuer (`rido-auth-service`), audience (`rido-api`)
- **Security Context Propagation**: Fixed case-sensitive header retrieval bug (X-User-Id → x-user-id)
- **Audience Validation**: Fixed to handle JWT audience as collection instead of single string
- **Header Injection**: Corrected Gateway filter to use explicit request mutation
- **Redis Configuration**: Added for both Gateway (blacklist) and Auth (rate limiting)
- **Spring Security Config**: Configured Gateway to permit all requests, preventing default Basic Auth blocking

### Key Security Features

- **Replay Attack Detection**: If a refresh token is reused, all user sessions are revoked
- **Token Rotation**: New refresh token issued on every use
- **Account Lockout**: Prevents brute-force attacks with Redis counters, auto-expires after 30 minutes
- **JWT Blacklisting**: Logout immediately invalidates access tokens via Redis
- **Key Rotation**: Supports manual key rotation without downtime

## 🚪 API Gateway Features (Kotlin)

### ✅ Completed

- **Routing**: Routes requests to appropriate microservices
  - `/auth/**` → Auth Service (public endpoints)
  - `/secure/**` → Auth Service (protected endpoints with JWT validation)
  - Future: `/trip/**`, `/matching/**`, `/payments/**`
- **RS256 JWT Validation**: 
  - Fetches JWKS from Auth service
  - Validates signature, issuer, audience, expiration
  - Checks token blacklist in Redis
- **Security Header Propagation**: Injects `X-User-ID` and `X-User-Roles` headers to downstream services
- **Error Handling**: Standardized 401 responses for invalid/missing/expired JWT
- **Spring Security Integration**: Custom config to prevent default auth interference

## 📚 Project Structure

```
rido-backend/
│
├── gateway/                      # Kotlin API Gateway
│   ├── filter/
│   │   └── JwtAuthGatewayFilterFactory.kt  # JWT validation & header injection
│   ├── crypto/
│   │   └── JwksLoader.kt         # JWKS fetching & caching
│   └── config/
│       └── SecurityConfig.kt     # Spring Security configuration
│
├── auth/                         # Java Auth Service
│   ├── controller/
│   │   ├── AuthController.java   # Register, login, refresh, logout
│   │   ├── SecureController.java # Protected endpoints (/secure/*)
│   │   ├── InternalAdminController.java  # Admin-only endpoints
│   │   └── KeyRotationController.java    # Key rotation
│   ├── service/
│   │   ├── AuthService.java      # Core auth logic
│   │   └── LoginAttemptService.java  # Brute-force protection
│   ├── config/
│   │   ├── SecurityConfig.java   # Spring Security + filters
│   │   └── SecurityContextFilter.java  # Header → SecurityContext
│   ├── crypto/
│   │   └── JwtKeyStore.java      # RSA key management
│   ├── model/
│   ├── repo/
│   └── Rido-Auth-API.postman_collection.json  # Postman tests
│
├── testing-scripts/              # Automated test scripts
│   ├── 01-debug-curl.sh
│   ├── 02-manual-security-test.sh
│   ├── 03-test-security-context.sh  # PRIMARY TEST
│   ├── 04-test-jwt-validation.sh
│   ├── 05-test-refresh-rotation.sh
│   ├── 06-test-refresh-token-hashing.sh
│   ├── 07-test-keyrotation-admin.sh
│   └── 08-test-secure-e2e.sh
│
└── infra/                        # Docker Compose setup
    └── docker-compose.yml        # All services + Redis + PostgreSQL
```

## 🚀 Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 21
- Kotlin
- Gradle
- `jq` (for testing scripts)

### Running Locally

1. **Start All Services**

   ```bash
   cd infra
   docker compose up -d
   ```

   Wait ~10 seconds for services to initialize.

2. **Verify Services**

   ```bash
   docker compose ps
   ```

   All services should show "Up" status.

3. **Run Tests**

   ```bash
   cd ../testing-scripts
   bash 03-test-security-context.sh
   ```

   Expected output:
   ```
   ✅ SecurityContext OK (userId = ...)
   ✅ Roles OK ({"authority": "ROLE_USER"})
   ```

## 🧪 Testing

### Automated Test Scripts

All scripts located in `testing-scripts/` directory:

1. **01-debug-curl.sh** - Debug with verbose curl output
2. **02-manual-security-test.sh** - Manual testing with detailed logs
3. **03-test-security-context.sh** ⭐ **PRIMARY TEST** - Verifies complete JWT → Gateway → Auth flow
4. **04-test-jwt-validation.sh** - JWT claims and signature validation
5. **05-test-refresh-rotation.sh** - Token rotation and security
6. **06-test-refresh-token-hashing.sh** - Refresh token storage verification
7. **07-test-keyrotation-admin.sh** - Admin key rotation endpoint
8. **08-test-secure-e2e.sh** - End-to-end secure endpoint testing

### Postman Collection

Import `auth/Rido-Auth-API.postman_collection.json` in Postman for interactive testing.

**Features:**
- Auto-saves access & refresh tokens
- Automated test assertions
- 5 categories: Authentication, Security Context, User Info, Session Management, JWKS

See `auth/POSTMAN_TESTING.md` for detailed guide.

## 🧪 API Endpoints

### Public Endpoints (No Auth Required)

#### Register
```http
POST /auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

#### Login
```http
POST /auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}

Response:
{
  "accessToken": "eyJraWQ...",
  "refreshToken": "a1b2c3d4..."
}
```

#### Refresh Token
```http
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "<your_refresh_token>"
}
```

#### Get JWKS (Public Keys)
```http
GET /auth/keys/jwks.json

Response:
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "625ec319-504f-4cec-b911-a8dec6431500",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

### Protected Endpoints (Requires Bearer Token via Gateway)

#### Test Security Context
```http
GET http://localhost:8080/secure/info
Authorization: Bearer <access_token>

Response:
{
  "userId": "d06979e1-178d-4071-a549-b6e22899e1fe",
  "roles": [
    {
      "authority": "ROLE_USER"
    }
  ]
}
```

#### Get User Profile
```http
GET /auth/me
X-User-ID: <user_id>

Response:
{
  "id": "...",
  "username": "testuser"
}
```

#### List Active Sessions
```http
GET /auth/sessions
X-User-ID: <user_id>

Response: [
  {
    "id": "...",
    "deviceId": "...",
    "createdAt": "2025-12-01T12:00:00Z",
    "expiresAt": "2025-12-06T12:00:00Z",
    "revoked": false
  }
]
```

#### Logout
```http
POST /auth/logout
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "refreshToken": "<refresh_token>"
}
```

### Admin-Only Endpoints

#### Rotate Keys (Admin)
```http
POST /internal/admin/rotate-keys
X-User-Roles: ADMIN
X-Admin-Secret: <admin_secret>
```

## 📊 Current Progress

| Service          | Status                            | Completion |
| ---------------- | --------------------------------- | ---------- |
| Auth Service     | JWT + Security Context ✅         | ~85%       |
| API Gateway      | JWT Validation + Routing ✅       | ~90%       |
| Matching Service | Not started                       | 0%         |
| Trips Service    | Not started                       | 0%         |
| Payments Service | Not started                       | 0%         |
| Infrastructure   | Docker Compose + Redis ✅         | ~70%       |

## 🔥 Recent Accomplishments

### Latest Work (Current Commit)

1. **✅ Fixed Security Context Propagation**
   - Resolved case-sensitive header bug (X-User-Id vs x-user-id)
   - Gateway correctly injects headers to Auth service
   - Auth service populates Spring Security context
   - Role-based access control working end-to-end

2. **✅ Fixed JWT Validation Flow**
   - Gateway validates RS256 signatures using JWKS
   - Proper issuer and audience claim validation
   - Token blacklist checking via Redis
   - Audience handled as collection instead of single string

3. **✅ Improved Testing Infrastructure**
   - Organized 8 automated test scripts
   - Created comprehensive Postman collection
   - All tests passing (security context, JWT validation, token rotation)
   - Removed unnecessary load tests

4. **✅ Configuration & Documentation**
   - Added Redis configuration for both services
   - Created POSTMAN_TESTING.md guide
   - Updated testing-scripts/README.md
   - Comprehensive walkthrough documentation

### Previous Work (Last Commit)

- JWKS routing whitelist configuration
- RS256 JWT verification with KID resolution
- Admin role enforcement
- Base64url-safe header parsing
- Internal endpoint protection
- Consistent 403/401 responses

## 🔒 Security Highlights

- **Stateless RS256 JWT**: Asymmetric signing, no shared secrets between services
- **JWKS Integration**: Gateway fetches public keys dynamically
- **Security Context Propagation**: Gateway → Auth via case-insensitive headers
- **Argon2id Password Hashing**: Industry-standard secure password storage
- **Refresh Token Rotation**: New token on every use, reduces compromise risk
- **Replay Attack Detection**: Automatic session revocation on token reuse
- **Account Lockout**: IP + user-based brute-force protection with Redis
- **Token Blacklisting**: Immediate invalidation on logout
- **Role-Based Access Control**: Spring Security @PreAuthorize with ROLE_ prefix

## 🐛 Known Issues & Solutions

### Security Context Not Populated
**Solution**: Fixed by using lowercase header names (`x-user-id`) in `SecurityContextFilter`

### Gateway Returns 401 for Valid Tokens
**Solutions Applied**:
- Fixed JWT audience validation to handle collections
- Configured Spring Security to permit all requests
- Added Redis connection for token blacklist
- Fixed header injection using explicit request mutation

### Redis Connection Errors
**Solution**: Added `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT` to both Gateway and Auth services

## 📝 License

This project is currently in development.

---

**Status**: 🏗 In Active Development | **Latest**: Security Context Propagation ✅ | **Next**: Matching Service (Redis GEO + Kafka)
