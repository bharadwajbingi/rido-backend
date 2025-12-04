# Rido — Real-Time Ride-Hailing Backend

> ⚠️ **PRODUCTION READINESS STATUS**: This codebase requires **4-6 weeks of critical fixes** before production deployment. See [Fix Surface Map](docs/fix_surface_map.md) for details.

A modern backend inspired by Uber/Ola, built with microservices, WebFlux, Redis, PostgreSQL, Kafka, Docker, and JWT.

## 📋 Project Status (2025-12-04)

**Current Phase**: Active Development + Comprehensive Analysis Completed  
**Production Ready**: ❌ NO - Critical security fixes required  
**Test Coverage**: ❌ 0% - Test suite not implemented  
**Security Audit**: ✅ Completed - [See Critical Gaps](#-critical-security-gaps)

### 📊 Comprehensive Analysis Deliverables

1. **[Capability Map](docs/capability_map.md)** - Complete feature inventory across all services (80+ files analyzed)
2. **[Testing Surface Map](docs/testing_surface_map.md)** - 380+ required test cases with gap analysis
3. **[Fix Surface Map](docs/fix_surface_map.md)** - 60+ critical fixes before testing (4-6 weeks effort)

## 🏗 Architecture Overview

Rido is designed with three operational microservices:

### 1. API Gateway (Kotlin + Spring Cloud Gateway)

- Central routing to backend services
- **RS256 JWT validation** with JWKS integration
- **Security context propagation** (X-User-Id, X-User-Roles headers)
- JWT audience/issuer validation
- Token blacklist checking (Redis)
- ⚠️ **Missing**: Circuit breakers, rate limiting, request logging

### 2. Auth Service (Java + Spring Boot)

- User registration, login, logout
- **Argon2id password hashing** (production-grade)
- **Refresh token rotation** with device binding
- **RS256 JWT signing** with rotating keys stored in Vault
- **Public JWKS endpoint** for Gateway validation
- **Dual-port architecture**: Public (8081) + Admin (9091)
- Redis-based rate limiting and account lockout
- **Session management**: Multi-device support, revocation
- ⚠️ **Missing**: Session limit enforcement, password reset, replay protection

### 3. Profile Service (Kotlin + R2DBC)

- User profile management (CRUD)
- Rider address management
- Driver document upload and verification
- Admin document approval/rejection
- Kafka event publishing (profile.updated, driver.approved)
- ⚠️ **CRITICAL**: Admin authorization NOT enforced - any user can approve documents
- ⚠️ **CRITICAL**: Document ownership NOT validated - users can upload for others

### 4. Future Services (Not Started)

- **Matching Service**: Driver assignment, Redis GEO, Kafka
- **Trips Service**: Trip lifecycle, state machine
- **Payments Service**: Payment processing, retry logic

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

## 📊 Service Maturity Assessment

| Service          | Features | Security | Stability | Tests | Production Ready? |
| ---------------- | -------- | -------- | --------- | ----- | ----------------- |
| Auth Service     | 70%      | 65%      | 60%       | 0%    | ❌ 4-6 weeks      |
| API Gateway      | 60%      | 70%      | 40%       | 0%    | ❌ 2-3 weeks      |
| Profile Service  | 65%      | **30%**  | 55%       | 0%    | ❌ **CRITICAL GAPS** |
| Infrastructure   | 70%      | 50%      | 60%       | N/A   | ❌ HA not configured |

## 🚨 Critical Security Gaps

### Profile Service (P0 - MUST FIX IMMEDIATELY)

1. **🔴 Admin Authorization Bypass** (2 hours to fix)
   - **Risk**: ANY authenticated user can approve/reject driver documents
   - **Impact**: Complete breakdown of document verification process
   - **Location**: `AdminController.kt` - missing role validation

2. **🔴 Document Ownership Vulnerability** (1 hour to fix)
   - **Risk**: User A can upload documents for User B's driver profile
   - **Impact**: Identity theft, document fraud, compliance violation
   - **Location**: `DriverDocumentController.kt` - no ownership check

### Auth Service (P0 - High Priority)

3. **🔴 Session Limit Not Enforced** (4 hours to fix)
   - **Risk**: Resource exhaustion, denial of service
   - **Config**: `max-active-sessions: 5` declared but not implemented

4. **🔴 Timing Attack Vulnerability** (4 hours to fix)
   - **Risk**: Username enumeration, account existence disclosure
   - **Impact**: Aids targeted attacks

### Gateway (P0 - High Priority)

5. **🔴 No Circuit Breakers** (1 day to fix)
   - **Risk**: Cascading failures across entire platform
   - **Impact**: Total system outage if one service degrades

6. **🔴 No Rate Limiting** (4 hours to fix)
   - **Risk**: DDoS vulnerability, backend overload
   - **Impact**: Service unavailability

### Infrastructure (P0)

7. **🔴 Single Point of Failure - Redis** (2 days to fix)
   - **Risk**: Total auth/rate-limiting failure if Redis crashes
   - **Solution**: Redis Sentinel/Cluster configuration

8. **🔴 Hardcoded Secrets** (4 hours to fix)
   - **Risk**: Credential leakage, security audit failure
   - **Location**: Vault token in `application.yml`, DB passwords in Profile

**Total P0 Fixes**: 24 critical issues, **4-6 weeks effort**

See [Fix Surface Map](docs/fix_surface_map.md) for complete remediation roadmap.

## 🔥 Recent Accomplishments

### Comprehensive Codebase Analysis (2025-12-04)

1. **✅ Complete Capability Mapping**
   - Analyzed 80+ files across 3 services
   - Documented all 23 endpoints, security mechanisms, data models
   - Identified all cross-service interactions
   - Mapped all external dependencies (Redis, PostgreSQL, Vault, Kafka)

2. **✅ Exhaustive Testing Surface Map**
   - Identified 380+ required test cases
   - Documented 90+ critical test gaps
   - Categorized by: endpoint tests, security tests, flow tests, load tests
   - Estimated effort: 16-20 weeks for comprehensive coverage

3. **✅ Critical Fix Identification**
   - 60+ fixes documented with code examples
   - Prioritized: P0 (4-6 weeks), P1 (3-4 weeks), P2 (2-3 weeks)
   - Security patches, architectural corrections, dependency fixes
   - Zero-downtime deployment requirements

### Previous Security Implementation

- ✅ RS256 JWT with JWKS rotation
- ✅ Refresh token rotation with device binding
- ✅ Argon2id password hashing
- ✅ Redis rate limiting and account lockout
- ✅ Dual-port admin architecture
- ✅ mTLS between Gateway and Auth

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

## 📚 Documentation

- **[Capability Map](docs/capability_map.md)** - Complete feature inventory and system capabilities
- **[Testing Surface Map](docs/testing_surface_map.md)** - Required test cases and coverage gaps
- **[Fix Surface Map](docs/fix_surface_map.md)** - Critical fixes and remediation timeline
- **[High Level Design](HIGH_LEVEL_DESIGN.md)** - Architecture and design decisions
- **[ADR 001](docs/adr/001-monorepo-structure.md)** - Monorepo structure decision

## 🎯 Next Steps (Prioritized)

### Immediate (Week 1-2) - Critical Security
1. Fix Profile admin authorization (2h)
2. Fix Profile document ownership (1h)  
3. Fix timing attack vulnerability (4h)
4. Remove hardcoded secrets (4h)
5. Add input validation (1d)

### Short-term (Week 3-6) - Stability
1. Implement circuit breakers (1d)
2. Configure Redis HA (2d)
3. Add Gateway rate limiting (4h)
4. Implement session limit enforcement (4h)
5. Add distributed tracing (1d)

### Medium-term (Week 7-12) - Testing
1. Implement P0 test suite (200+ tests)
2. Add integration tests
3. Performance/load testing
4. Security penetration testing

---

**Status**: 🏗 Active Development + Analysis Complete | **Critical**: Security Fixes Required | **Timeline**: 4-6 weeks to MVP | **Next**: Fix Profile Authorization
