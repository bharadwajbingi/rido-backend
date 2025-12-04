# Complete Codebase Capability Map

**Generated:** 2025-12-04  
**Services Analyzed:** Auth, Gateway, Profile  
**Total Files Scanned:** 80+  
**Analysis Depth:** Exhaustive (all 13 categories)

---

## 🔐 AUTH SERVICE

### 1. Features Implemented

#### Core Authentication
- **User Registration** - Argon2 password hashing, username uniqueness checks
- **User Login** - Credential validation, JWT generation (access + refresh tokens)
- **Token Refresh** - Refresh token rotation with device binding
- **Logout** - Token blacklisting (access token JTI in Redis, refresh token revocation in DB)
- **Session Management** - Multi-device session tracking, revoke individual/all sessions
- **Admin Authentication** - Separate admin port (9091) with admin-specific JWT auth
- **Admin Creation** - Authenticated admins can create new admins via dedicated endpoint
- **Bootstrap Admin** - Auto-creates first admin on startup if none exists (env-configured)

#### Security Features
- **Account Lockout** - 5 failed attempts → 30-minute lockout (Redis + DB persistence)
- **Rate Limiting** - Sliding window rate limiter using Redis sorted sets
- **JWT Key Rotation** - RSA 2048-bit keys stored in Vault, admin-triggered rotation
- **Token Blacklist** - Access token JTI blacklisting in Redis with TTL
- **Replay Protection** - Device ID validation on refresh
- **mTLS Authentication** - Service-to-service mTLS via X.509 certificates
- **JWKS Publishing** - Public key distribution at `/.well-known/jwks.json`

#### Audit & Monitoring
- **Audit Logging** - Database-backed audit trail for all security events
- **Metrics Collection** - Prometheus metrics (login attempts, lockouts, token operations)
- **Structured Logging** - JSON structured logs with correlation IDs

### 2. All Endpoints

#### Port 8081/8443 (Public API - mTLS required)

**Public (No Auth Required)**
- `GET /auth/check-username?username={username}` - Check username availability
- `POST /auth/register` - User registration (rate limit: 10/60s per IP)
- `POST /auth/login` - User login (rate limit: 50/60s per IP, 10/300s per user)
- `POST /auth/refresh` - Refresh access token (rate limit: 20/60s per IP)
- `POST /auth/logout` - Logout and blacklist tokens
- `GET /auth/keys/.well-known/jwks.json` - Public JWKS endpoint
- `GET /auth/keys/jwks.json` - Public JWKS endpoint (alternative)

**Authenticated (JWT Required)**
- `GET /auth/me` - Get current user info
- `GET /auth/sessions` - List active sessions
- `POST /auth/sessions/revoke-all` - Revoke all sessions
- `POST /auth/sessions/{sessionId}/revoke` - Revoke specific session

**Deprecated (Backward Compat)**
- `GET /secure/info` - Returns user info (deprecated, use /auth/me)

**Debug (dev/test profiles only)**
- `POST /auth/debug/unlock` - Unlock account for testing

#### Port 9091 (Admin API - No mTLS, VPN/SSH access only)

**Public Admin Endpoints**
- `GET /admin/health` - Admin port health check
- `POST /admin/login` - Admin login (returns admin JWT)

**Protected Admin Endpoints (Admin JWT Required)**
- `POST /admin/create` - Create new admin user
- `POST /admin/key/rotate` - Rotate JWT signing key
- `GET /admin/audit/logs?page=0&size=50` - Get audit logs (paginated, max 100 per page)

### 3. Background Tasks, Schedulers, Listeners

**Scheduled Tasks**
- `SessionCleanupService.cleanup()` - Cron: `0 0 */6 * * *` (every 6 hours)
  - Bulk deletes expired/revoked refresh tokens from database
  - Query: `DELETE FROM refresh_tokens WHERE (expires_at < NOW() OR revoked = true)`

**Initialization Tasks**
- `BootstrapAdminService.bootstrapAdmin()` - `@PostConstruct`
  - Runs on startup, creates first admin if none exists
  - Reads `${FIRST_ADMIN_USERNAME}` and `${FIRST_ADMIN_PASSWORD}` from environment
  - Skipped if any admin already exists or password not configured
  
- `JwtKeyStore.init()` - `@PostConstruct`
  - Loads JWT signing keys from Vault on startup
  - Falls back to generating new keys if Vault unavailable
  - Persists generated keys to Vault

**Event Listeners**
- None (no Kafka/RabbitMQ consumers in Auth service)

### 4. All Security Checks

#### Authentication Filters (Spring Security Chain)

**Admin Filter Chain (Port 9091, Order 1)**
- `AdminAuthenticationFilter` - JWT validation for admin endpoints
  - Validates admin JWT signature using JwtKeyStore
  - Extracts `userId` and `role` claims
  - Sets `userId` attribute in request
  - Bypasses `/admin/login` and `/admin/health`

**User Filter Chain (Port 8081/8443, Order 2)**
- `ServiceAuthenticationFilter` - mTLS certificate validation
  - Extracts CN from X.509 client certificate
  - Sets `X-Service-Name` attribute (for future RBAC)
  - Applied to ALL requests (gateway → auth requires mTLS)
  
- `JwtUserAuthenticationFilter` - JWT validation for user endpoints
  - Validates JWT signature using JwtKeyStore
  - Checks token blacklist (JTI in Redis)
  - Extracts `userId`, `jti`, `roles` claims
  - Sets `userId` attribute in request
  - Bypasses public endpoints (login, register, refresh, etc.)

#### Business Logic Security

**Rate Limiting (`RateLimiterService`)**
- Sliding window rate limiter using Redis sorted sets
- Applied to:
  - `POST /auth/register` - 10 requests/60s per IP
  - `POST /auth/login` - 50 requests/60s per IP, 10 requests/300s per username (on failure)
  - `POST /auth/refresh` - 20 requests/60s per IP

**Account Lockout (`LoginAttemptService`)**
- Tracks failed login attempts in Redis (`auth:login:attempts:{username}`)
- 5 failed attempts → 30-minute lockout
- Lockout stored in:
  - Redis: `auth:login:locked:{username}` (TTL: 30 minutes)
  - Database: `users.locked_until` (persistent)
- Auto-unlock on expiry or successful login
- **SKIP LOCKOUT FOR ADMINS** - Admin accounts exempt from lockout

**Device Binding (`AuthService.refresh`)**
- Validates `X-Device-Id` header matches refresh token's `device_id`
- Throws `DeviceMismatchException` if mismatch detected

**Token Blacklist (`TokenBlacklistService`)**
- Blacklists access token JTI in Redis on logout
- Checks JTI blacklist on every JWT validation
- TTL set to token's remaining lifetime (`exp - now`)

**Replay Protection**
- Nonce-based replay protection NOT implemented
- Device ID validation provides limited replay protection

### 5. All Data Models

#### Entities (JPA - PostgreSQL)

**UserEntity** (`users` table)
```
- id: UUID (PK)
- username: String (unique, max 150 chars)
- passwordHash: String (Argon2)
- role: String ("USER" | "ADMIN")
- createdAt: Instant
- failedLoginAttempts: int
- accountLocked: boolean
- lockoutEndTime: Instant (deprecated field, use lockedUntil)
- lockedUntil: Instant
```

**RefreshTokenEntity** (`refresh_tokens` table)
```
- id: UUID (PK)
- userId: UUID (FK to users)
- tokenHash: String (SHA-256, max 256 chars)
- expiresAt: Instant
- revoked: boolean
- deviceId: String
- userAgent: String
- ip: String
- jti: UUID
- createdAt: Instant
```

**AuditLog** (`audit_logs` table)
```
- id: UUID (PK)
- eventType: AuditEvent (enum)
- userId: UUID
- username: String (max 150 chars)
- ipAddress: String (max 45 chars, IPv6 support)
- deviceId: String (max 255 chars)
- userAgent: String (max 500 chars)
- success: boolean
- failureReason: String (max 500 chars)
- metadata: String (TEXT, JSON)
- timestamp: Instant
Indexes: user_id, event_type, timestamp, username
```

**AuditEvent Enum**
```java
LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT,
ADMIN_LOGIN, ADMIN_CREATED, KEY_ROTATED,
REGISTRATION, TOKEN_REFRESH, SESSION_REVOKED
```

#### DTOs
- `LoginRequest(username, password)`
- `RegisterRequest(username, password)`
- `RefreshRequest(refreshToken)`
- `LogoutRequest(refreshToken)`
- `TokenResponse(accessToken, refreshToken, expiresIn, tokenType)`
- `SessionDTO(id, deviceId, ip, userAgent, revoked, createdAt, expiresAt)`
- `ErrorResponse(error, message, timestamp)`

#### Exceptions
- `InvalidCredentialsException` - 401
- `AccountLockedException` - 423 Locked
- `UsernameAlreadyExistsException` - 409 Conflict
- `TooManyRequestsException` - 429
- `TokenExpiredException` - 401
- `DeviceMismatchException` - 401
- `ReplayDetectedException` - 401

### 6. Cross-Service Interactions

**Outbound (Auth → Other Services)**
- None (Auth is an isolated service, no outbound HTTP calls)

**Inbound (Other Services → Auth)**
- **Gateway → Auth (mTLS)**
  - All `/auth/**` requests proxied from Gateway to `https://auth:8443`
  - mTLS certificate validation required
  - Gateway extracts and forwards user headers (`X-User-ID`, `X-User-Role`)

- **Gateway JWKS Fetch**
  - Gateway periodically fetches JWKS from `https://auth:8443/auth/keys/jwks.json`
  - Used to validate JWTs at gateway level (for non-auth routes)
  - Scheduled refresh every 10 seconds

**Inter-Service Protocol**
- mTLS (Mutual TLS) for all service-to-service communication
- Certificates stored in `infra/mtls-certs/`
- CN-based service identification

### 7. All External Dependencies

**Database**
- **PostgreSQL** (`postgres:5432`, database: `ride_hailing`)
- Connection via JDBC: `jdbc:postgresql://postgres:5432/ride_hailing`
- Credentials injected from Vault: `spring.datasource.username`, `spring.datasource.password`
- Migration: Flyway (baseline-on-migrate enabled)

**Cache/Session Store**
- **Redis** (`redis:6379`)
- Uses: Rate limiting, account lockout, token blacklist
- Key patterns:
  - `rate:{key}` - Sorted sets for rate limiting
  - `auth:login:attempts:{username}` - Failed login counter
  - `auth:login:locked:{username}` - Lockout flag
  - `auth:jti:blacklist:{jti}` - Blacklisted access tokens

**Secrets Management**
- **HashiCorp Vault** (`http://localhost:8200`)
- Authentication: Token-based (dev mode: `root`)
- Paths:
  - `secret/data/auth/keys` - JWT signing keys (kid, privateKey, publicKey)
  - `secret/auth` - Database credentials (injected via Spring Cloud Vault)
- KV engine: version 2 (`secret/`)

**Security**
- **mTLS Certificates** - X.509 client certificates for service authentication
- **JWT (RS256)** - JJWT library (`io.jsonwebtoken`)
- **Argon2** - Password hashing (`Argon2PasswordEncoder`)
  - Params: saltLength=16, hashLength=32, parallelism=1, memory=4096, iterations=3

**Monitoring & Observability**
- **Prometheus** - Metrics export via Spring Boot Actuator
- **Actuator Endpoints** - All endpoints exposed: `/actuator/**`
- **Micrometer** - Custom counters for auth events

**Build & Runtime**
- **Spring Boot 3.x** - Web framework
- **Java 17+** - Runtime
- **Gradle** - Build tool

### 8. All Places Where Auth is Enforced

#### Filter-Level Auth
1. **AdminAuthenticationFilter** - `/admin/**` (except `/admin/login`, `/admin/health`)
   - Validates admin JWT
   - Requires `role=ADMIN` claim
   
2. **JwtUserAuthenticationFilter** - All authenticated endpoints
   - Validates user JWT
   - Checks JTI blacklist
   - Requires valid signature + non-expired token

#### Endpoint-Level Auth (Spring Security Config)
- `@PreAuthorize` annotations NOT used (filter-based auth)
- SecurityFilterChain rules:
  - Admin chain: `anyRequest().authenticated()` (after public endpoints)
  - User chain: `anyRequest().authenticated()` (after public endpoints)

#### Business Logic Auth
- **Session Ownership** - `AuthController.revokeOne()`
  - Validates session belongs to authenticated user
  - Throws `InvalidCredentialsException` if mismatch

- **Admin Role Check** - `LoginAttemptService.onFailure()`
  - Skips lockout for users with `role=ADMIN`

### 9. All Rate-Limiters, Throttling, Filtering

**Rate Limiter Implementation**
- `RateLimiterService` - Redis-based sliding window
- Algorithm: Sorted set with timestamp scores
- Automatically cleans old entries outside window

**Applied Rate Limits**
| Endpoint | Key | Max Requests | Window | Throws |
|----------|-----|--------------|--------|--------|
| `POST /auth/register` | `register:{ip}` | 10 | 60s | `TooManyRequestsException` |
| `POST /auth/login` (success path) | `login:ip:{ip}` | 50 | 60s | `TooManyRequestsException` |
| `POST /auth/login` (failure path) | `login:user:{username}` | 10 | 300s | `TooManyRequestsException` |
| `POST /auth/refresh` | `refresh:{ip}` | 20 | 60s | `TooManyRequestsException` |

**Filtering**
- **Null Byte Injection** - `check-username` endpoint rejects `\u0000` characters
- **Cookie Removal** - Gateway strips `Cookie` header to prevent CSRF

**Throttling**
- No explicit request throttling beyond rate limiting
- Database connection pooling limits concurrent queries

### 10. All Stateful Flows

#### User Registration Flow
1. Client → `POST /auth/register` (username, password)
2. Rate limit check (10/60s per IP)
3. Username availability check
4. Argon2 password hashing
5. Create `UserEntity` (role=USER)
6. Audit log `REGISTRATION` event
7. Return success

#### User Login Flow
1. Client → `POST /auth/login` (username, password, headers: X-Device-Id, User-Agent)
2. Check account lockout (Redis + DB)
3. Rate limit check (50/60s per IP)
4. Validate credentials (Argon2 verify)
5. On success:
   - Clear failed attempts
   - Generate access JWT (5 min TTL, RS256, kid, jti, iss, aud, roles)
   - Generate refresh token (14 days TTL, SHA-256 hash stored)
   - Store `RefreshTokenEntity` (deviceId, ip, userAgent)
   - Audit log `LOGIN_SUCCESS`
   - Return `TokenResponse`
6. On failure:
   - Increment failed attempts (Redis: `auth:login:attempts:{username}`)
   - If attempts > 5: Lock account (Redis + DB, 30 min)
   - Rate limit (10/300s per username)
   - Audit log `LOGIN_FAILURE`
   - Throw `InvalidCredentialsException`

#### Token Refresh Flow
1. Client → `POST /auth/refresh` (refreshToken, headers: X-Device-Id)
2. Rate limit check (20/60s per IP)
3. SHA-256 hash refresh token
4. Find `RefreshTokenEntity` by hash
5. Validate:
   - Token not revoked
   - Token not expired
   - Device ID matches
6. Rotate refresh token:
   - Revoke old token
   - Generate new refresh token
   - Update `RefreshTokenEntity`
7. Generate new access JWT
8. Return `TokenResponse`

#### Logout Flow
1. Client → `POST /auth/logout` (refreshToken, headers: Authorization)
2. Revoke refresh token in DB
3. Blacklist access token JTI in Redis (TTL = remaining token lifetime)
4. Audit log `LOGOUT`
5. Return success

#### Session Management Flow
1. **List Sessions** - `GET /auth/sessions`
   - Query `RefreshTokenEntity` by userId
   - Return session DTOs sorted by createdAt (desc)

2. **Revoke All Sessions** - `POST /auth/sessions/revoke-all`
   - Batch update: `UPDATE refresh_tokens SET revoked=true WHERE user_id=?`

3. **Revoke Single Session** - `POST /auth/sessions/{sessionId}/revoke`
   - Validate session ownership
   - Update: `UPDATE refresh_tokens SET revoked=true WHERE id=?`

#### Admin Login Flow
1. Client → `POST /admin/login` (username, password) [Port 9091]
2. Validate credentials (Argon2 verify)
3. Check `role=ADMIN`
4. Generate admin JWT (access token only, no refresh)
5. Audit log `ADMIN_LOGIN`
6. Return `TokenResponse` (refreshToken=null)

#### Admin Creation Flow
1. Client → `POST /admin/create` (username, password, headers: admin JWT) [Port 9091]
2. AdminAuthenticationFilter validates admin JWT
3. Check username availability
4. Create `UserEntity` (role=ADMIN, passwordHash=Argon2)
5. Audit log `ADMIN_CREATED` (creatorId from JWT)
6. Return success

#### Key Rotation Flow
1. Admin → `POST /admin/key/rotate` (headers: admin JWT) [Port 9091]
2. AdminAuthenticationFilter validates admin JWT
3. `JwtKeyStore.rotate()`:
   - Generate new RSA 2048-bit keypair
   - Assign new KID (UUID)
   - Store in `keys` map (keeps old keys for verification)
   - Persist to Vault (`secret/data/auth/keys`)
   - Set new KID as active
4. Audit log `KEY_ROTATED`
5. Return new KID

**Note:** Old tokens remain valid until expiry (old keys retained in keystore)

### 11. All Features Started but Not Finished

**Identified Incomplete Features:**
1. **Replay Protection** - Device ID validation exists but nonce-based replay detection NOT implemented
2. **Service-to-Service RBAC** - `ServiceAuthenticationFilter` extracts service name but no authorization policies defined
3. **Account Lockout Field Duplication** - `UserEntity` has both `lockoutEndTime` and `lockedUntil` (inconsistent usage)
4. **Password Reset** - No password reset/recovery flow implemented
5. **Email Verification** - No email verification on registration
6. **2FA/MFA** - No multi-factor authentication
7. **Role-Based Access Control** - Roles extracted but no fine-grained permissions
8. **Token Revocation List** - Only JTI blacklisting, no full token revocation list
9. **Geo-blocking** - No IP-based geo-restrictions
10. **Session Limits** - ✅ Config has `max-active-sessions: 5` and enforcement IMPLEMENTED (2025-12-04)

### 12. All TODOs, FIXMEs, Unused Classes, Dead Code

**TODOs/FIXMEs**
- None found in codebase (search returned 0 results)

**Unused/Deprecated Code**
1. **SecureController** (`/secure/info` endpoint)
   - Marked as "deprecated" in SecurityConfig comments
   - Kept for backward compatibility
   - Recommendation: Remove after migration period

2. **UserEntity.lockoutEndTime** field
   - Duplicate of `lockedUntil`
   - Not actively used
   - Recommendation: Remove in next schema migration

3. **DebugController** (`/auth/debug/unlock`)
   - Only active in `dev` and `test` profiles
   - Exposes account unlock functionality
   - **SECURITY RISK:** Ensure disabled in production

**Potential Dead Code**
- `RateLimiterService.reset()` method
  - Only used by test utilities
  - No production usage found

### 13. All Config-Based Behaviors

**Environment Variables**
- `FIRST_ADMIN_USERNAME` (default: `admin`) - Bootstrap admin username
- `FIRST_ADMIN_PASSWORD` (required) - Bootstrap admin password (empty = skip bootstrap)
- `JWT_ACCESS_TTL` (default: `300` seconds) - Access token lifetime
- `JWT_REFRESH_TTL` (default: `1209600` seconds = 14 days) - Refresh token lifetime

**Application Properties (application.yml)**
```yaml
server.port: 8081  # Public API port
admin.server.port: 9091  # Admin API port

auth.login:
  max-failed-attempts: 5
  lockout-duration-seconds: 300  # 5 minutes
  max-active-sessions: 5  # ✅ ENFORCED (2025-12-04)

jwt:
  accessTokenTtlSeconds: ${JWT_ACCESS_TTL:300}
  refreshTokenTtlSeconds: ${JWT_REFRESH_TTL:1209600}

spring.cloud.vault:
  uri: http://localhost:8200
  authentication: TOKEN
  token: root  # ⚠️ Dev mode only
  kv.backend: secret
  kv.default-context: auth

spring.datasource:
  url: jdbc:postgresql://postgres:5432/ride_hailing
  # username/password injected from Vault

spring.data.redis:
  host: redis
  port: 6379

management.endpoints.web.exposure.include: "*"  # All actuator endpoints exposed
```

**Profile-Based Behavior**
- `@Profile({"dev", "test"})` - DebugController only active in dev/test
- Production profile should explicitly disable debug endpoints

**Security Configuration**
- Dual security filter chains (admin port vs user port)
- mTLS enforcement configurable via certificate presence

**Feature Flags**
- None explicitly defined
- Behavior controlled via environment variables and Spring profiles

---

## 🌐 GATEWAY SERVICE

### 1. Features Implemented

#### Core Routing
- **Request Proxying** - Spring Cloud Gateway routes to Auth and Profile services
- **JWT Validation** - Validates user JWTs before forwarding to downstream services
- **JWKS Caching** - Periodic JWKS refresh from Auth service
- **Header Injection** - Injects `X-User-ID` and `X-User-Role` headers for downstream services
- **CORS Configuration** - Cross-origin request handling

#### Security Features
- **JWT Signature Verification** - RS256 validation using JWKS
- **Token Blacklist Check** - Redis lookup for revoked JTI
- **ISS/AUD Validation** - Validates issuer (`rido-auth-service`) and audience (`rido-api`)
- **KID-based Key Resolution** - Resolves public keys by `kid` claim

### 2. All Endpoints

**Gateway Routes (Port 8080)**

All routes use `https://auth:8443` or `http://profile:8080` as upstream URIs.

**Auth Service Routes**
- `GET /auth/me` → `https://auth:8443/auth/me` (JwtAuth filter applied)
- `GET /auth/sessions` → `https://auth:8443/auth/sessions` (JwtAuth filter applied)
- `POST /auth/sessions/**` → `https://auth:8443/auth/sessions/**` (JwtAuth filter applied)
- `GET /secure/info` → `https://auth:8443/secure/info` (JwtAuth filter applied)
- `POST|GET /auth/**` → `https://auth:8443/auth/**` (No JWT filter, public routes)
  - Includes: `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/auth/keys/**`
  - Cookie header removed for CSRF protection

**Profile Service Routes**
- `ANY /profile/**` → `http://profile:8080/profile/**` (JwtAuth filter applied)
  - All profile endpoints require JWT authentication

**Actuator**
- `/actuator/**` - Gateway management endpoints (exposed: all)
- `/actuator/gateway/routes` - View active routes

### 3. Background Tasks, Schedulers, Listeners

**Scheduled Tasks**
1. **JwksRefresher** - `@Scheduled(fixedDelay = 10000)` (every 10 seconds)
   - Fetches JWKS from `${JWKS_URL}` (typically `https://auth:8443/auth/keys/jwks.json`)
   - Parses keys and updates `JwtKeyResolver` cache
   - Handles network failures gracefully (logs error, continues)

2. **JwksLoader** - `@Scheduled(fixedDelay = 10000)` (every 10 seconds)
   - Similar to JwksRefresher (possible duplicate implementation)
   - Loads JWKS keys into resolver

**Event Listeners**
- None

### 4. All Security Checks

**JwtAuthGatewayFilterFactory** (Custom Gateway Filter)
- Applied to authenticated routes via `filters: - JwtAuth` in route config
- Validation steps:
  1. Extract `Authorization: Bearer {token}` header
  2. Decode JWT header (Base64 decode, no signature verification yet)
  3. Validate `alg=RS256` (reject other algorithms)
  4. Extract `kid` from header
  5. Resolve public key from `JwtKeyResolver` (JWKS cache)
  6. Parse and verify JWT signature using JJWT library
  7. Validate `iss=rido-auth-service` and `aud=rido-api`
  8. Extract `jti` and check blacklist in Redis (`auth:jti:blacklist:{jti}`)
  9. If blacklisted: Return 401 Unauthorized
  10. Extract `userId` (subject) and `roles` claims
  11. Inject headers: `X-User-ID: {userId}`, `X-User-Role: {roles}`
  12. Forward request to downstream service

**Route-Specific Bypasses**
- ALL `/auth/**` routes bypass JWT filter (even authenticated endpoints)
- Auth service validates JWT itself (Gateway acts as passthrough for auth routes)

**CORS Security**
- `CorsConfig` defines allowed origins, methods, headers
- Applied globally to all routes

**SecurityConfig**
- WebFlux security: `csrf().disable()`, `authorizeExchange().anyExchange().permitAll()`
- No Spring Security authentication (custom JWT filter handles auth)

### 5. All Data Models

**None (Stateless Gateway)**
- No database entities
- No persistent storage
- Only in-memory JWKS cache

**DTOs**
- None explicitly defined (passthrough JSON)

### 6. Cross-Service Interactions

**Outbound (Gateway → Downstream Services)**
1. **Gateway → Auth (mTLS)**
   - URI: `https://auth:8443`
   - Protocol: HTTPS with mTLS
   - Certificates: Client cert for gateway service
   - Routes: All `/auth/**` requests

2. **Gateway → Profile (HTTP)**
   - URI: `http://profile:8080`
   - Protocol: HTTP (no mTLS)
   - Routes: All `/profile/**` requests

3. **Gateway → Auth JWKS** (Scheduled)
   - URI: `${JWKS_URL}` (e.g., `https://auth:8443/auth/keys/jwks.json`)
   - Frequency: Every 10 seconds
   - Purpose: Refresh public keys for JWT validation

**Inbound (Client → Gateway)**
- External clients hit Gateway on port `8080`
- Gateway performs JWT validation and forwards to backend services

**Header Propagation**
- Downstream headers injected:
  - `X-User-ID` - User UUID from JWT subject
  - `X-User-Role` - Comma-separated roles (e.g., `USER,ADMIN`)
- Removed headers:
  - `Cookie` - Stripped from `/auth/**` requests (CSRF protection)

### 7. All External Dependencies

**Cache**
- **Redis** (`redis:6379`)
- Uses: Token blacklist lookup (`auth:jti:blacklist:{jti}`)
- Library: `ReactiveStringRedisTemplate`

**HTTP Clients**
- **WebClient** - Reactive HTTP client for JWKS fetching
- **GatewayHttpClientConfig** - Custom HTTP client for mTLS (GatewayClient)
- **JwksWebClient** - Dedicated WebClient for JWKS refresh

**Security**
- **JJWT** - JWT parsing and validation (`io.jsonwebtoken.Jwts`)
- **mTLS Certificates** - X.509 client certificates for Auth service communication

**Routing**
- **Spring Cloud Gateway** - Reactive gateway framework
- **WebFlux** - Reactive web stack

**Build & Runtime**
- **Kotlin** - Primary language
- **Spring Boot 3.x**
- **Gradle** - Build tool

### 8. All Places Where Auth is Enforced

**Gateway Filter Level**
- `JwtAuthGatewayFilterFactory` applied to:
  - `/auth/me`
  - `/auth/sessions`
  - `/auth/sessions/**`
  - `/secure/info`
  - `/profile/**`

**Auth Bypass**
- ALL `/auth/**` routes (except explicitly filtered ones above)
  - Gateway does NOT validate JWT for auth service routes
  - Auth service performs its own authentication

**No Enforcement**
- `/actuator/**` - Publicly accessible (should be restricted in production)

### 9. All Rate-Limiters, Throttling, Filtering

**Rate Limiting**
- None at gateway level
- Relies on downstream services (Auth service has rate limiting)

**Throttling**
- None explicitly configured
- No circuit breakers or backpressure mechanisms

**Filtering**
- **Cookie Removal** - `RemoveRequestHeader=Cookie` on `/auth/**` routes
- **Host Header Preservation** - `PreserveHostHeader` on `/profile/**` routes

### 10. All Stateful Flows

**JWT Validation Flow**
1. Client → Gateway with `Authorization: Bearer {token}`
2. JwtAuthGatewayFilterFactory extracts token
3. Decode header, validate `alg` and extract `kid`
4. Resolve public key from `JwtKeyResolver` (JWKS cache)
5. Verify JWT signature
6. Validate `iss` and `aud`
7. Check JTI blacklist in Redis
8. If valid: Inject `X-User-ID` and `X-User-Role` headers
9. Forward request to downstream service
10. Return response to client

**JWKS Refresh Flow**
1. Scheduled task (every 10s) triggers `JwksRefresher.refresh()`
2. HTTP GET `https://auth:8443/auth/keys/jwks.json`
3. Parse JSON response `{"keys": [{kid, n, e, ...}]}`
4. For each key: `JwtKeyResolver.addFromJwk(kid, n, e)`
5. Resolver builds RSA public key from modulus (n) and exponent (e)
6. Updates in-memory cache
7. Log success/failure

### 11. All Features Started but Not Finished

1. **Circuit Breakers** - No resilience patterns implemented (Resilience4j not configured)
2. **Request Tracing** - No distributed tracing (OpenTelemetry partially configured in infra but not integrated)
3. **Gateway Rate Limiting** - No rate limiting at gateway level
4. **Service Discovery** - `spring.cloud.gateway.discovery.locator.enabled: false` (static routes only)
5. **Authentication Caching** - JWT validated on every request (no caching of validation results)

### 12. All TODOs, FIXMEs, Unused Classes, Dead Code

**TODOs/FIXMEs**
- None found

**Potential Issues**
1. **Duplicate JWKS Refreshers** - Both `JwksRefresher` and `JwksLoader` exist (may be duplicate)
2. **Commented Code** - In `application.yml`:
   ```yaml
   # - SetRequestHeader=X-Forwarded-Proto, http
   ```
   Commented header injection (why?)

**Dead Code**
- None identified

### 13. All Config-Based Behaviors

**Environment Variables**
- `JWKS_URL` - URL to fetch JWKS from Auth service (e.g., `https://auth:8443/auth/keys/jwks.json`)

**Application Properties (application.yml)**
```yaml
server.port: 8080

spring.data.redis:
  host: redis
  port: 6379

spring.cloud.gateway:
  discovery.locator.enabled: false  # No service discovery

  routes:
    - id: auth-me
      uri: https://auth:8443
      predicates: [Path=/auth/me]
      filters: [JwtAuth]

    - id: auth-sessions
      uri: https://auth:8443
      predicates: [Path=/auth/sessions,/auth/sessions/**]
      filters: [JwtAuth]

    - id: auth-public
      uri: https://auth:8443
      predicates: [Path=/auth/**]
      filters: [RemoveRequestHeader=Cookie]

    - id: secure-info
      uri: https://auth:8443
      predicates: [Path=/secure/info]
      filters: [JwtAuth]

    - id: profile-service
      uri: http://profile:8080
      predicates: [Path=/profile/**]
      filters: [JwtAuth, PreserveHostHeader]

management.endpoints.web.exposure.include: "*"  # All actuator endpoints
```

**Hardcoded Values**
- JWT validation expects:
  - `iss=rido-auth-service`
  - `aud=rido-api`
  - `alg=RS256`
- Redis key pattern: `auth:jti:blacklist:{jti}`

**Feature Flags**
- None

---

## 👤 PROFILE SERVICE

### 1. Features Implemented

#### Core Profile Management
- **User Profile CRUD** - Get and update user profile (name, phone, email, photoUrl, role, status)
- **Photo Upload** - Generate pre-signed upload URLs for profile photos
- **Multi-Role Support** - Rider, Driver, Admin roles

#### Rider Features
- **Address Management** - Save/list/delete rider addresses (home, work, custom locations)
- **Geolocation Storage** - Lat/lng coordinates for addresses

#### Driver Features
- **Document Upload** - Drivers upload license, registration, insurance, permit documents
- **Document Verification** - Admins approve/reject driver documents
- **Driver Stats** - Track total trips, cancelled trips, rating, earnings (not fully implemented)

#### Admin Features
- **Document Review** - Approve/reject driver documents with reasons
- **Audit Logging** - Track all profile changes and admin actions

#### Event Publishing
- **Kafka Events** - Publish profile.updated, driver.document.uploaded, driver.approved, driver.rejected events

### 2. All Endpoints

**Port 8080 (All require JWT - X-User-ID header)**

#### Profile Controller (`/profile`)
- `GET /profile/me` - Get current user profile
- `PUT /profile/me` - Update current user profile (name, phone, email)
- `POST /profile/me/photo` - Generate pre-signed photo upload URL

#### Address Controller (`/profile/rider/addresses`)
- `GET /profile/rider/addresses` - List rider's saved addresses
- `POST /profile/rider/addresses` - Add new address (label, lat, lng)
- `DELETE /profile/rider/addresses/{id}` - Delete saved address

#### Driver Document Controller (`/profile/driver/documents`)
- `GET /profile/driver/documents` - List driver's uploaded documents
- `POST /profile/driver/documents` - Upload new document (type, documentNumber, url)

#### Admin Controller (`/profile/admin/driver`)
- `POST /profile/admin/driver/{id}/approve` - Approve driver document
- `POST /profile/admin/driver/{id}/reject` - Reject driver document with reason

### 3. Background Tasks, Schedulers, Listeners

**Scheduled Tasks**
- None

**Event Listeners**
- None (Profile service is a producer-only service)

**Kafka Producers**
- `ProfileEventProducer` publishes events to Kafka topics

### 4. All Security Checks

**Header-Based Authentication**
- All endpoints expect `X-User-ID` header (injected by Gateway)
- No JWT validation at service level (trusts Gateway)
- No Spring Security filters (all endpoints assume authenticated user)

**Authorization**
- **Address Ownership** - `AddressService.deleteAddress()` validates address belongs to user
- **Admin Role Check** - Admin endpoints expect admin role but NOT enforced at controller level
  - **SECURITY GAP:** No validation that `X-User-Role` contains `ADMIN`

**Data Validation**
- Jakarta Bean Validation (`@Valid`) on request DTOs
- No custom validation interceptors

### 5. All Data Models

#### Entities (R2DBC - PostgreSQL Reactive)

**UserProfile** (`user_profiles` table)
```kotlin
- id: UUID (PK, auto-generated)
- userId: UUID (FK to auth.users, unique)
- name: String
- phone: String
- email: String
- photoUrl: String? (nullable)
- role: UserRole (enum: RIDER, DRIVER, ADMIN)
- status: UserStatus (enum: ACTIVE, BANNED, PENDING_VERIFICATION, default: ACTIVE)
- createdAt: Instant
- updatedAt: Instant
```

**RiderAddress** (`rider_addresses` table)
```kotlin
- id: UUID (PK, auto-generated)
- riderId: UUID (FK to user_profiles.userId)
- label: String (e.g., "Home", "Work")
- lat: Double (latitude)
- lng: Double (longitude)
- createdAt: Instant
```

**DriverDocument** (`driver_documents` table)
```kotlin
- id: UUID (PK, auto-generated)
- driverId: UUID (FK to user_profiles.userId)
- type: DocumentType (enum: LICENSE, REGISTRATION, INSURANCE, PERMIT)
- documentNumber: String
- url: String (S3/storage URL)
- status: DocumentStatus (enum: PENDING, APPROVED, REJECTED, default: PENDING)
- reason: String? (rejection reason, nullable)
- uploadedAt: Instant
- reviewedBy: UUID? (admin ID, nullable)
```

**DriverStats** (`driver_stats` table)
```kotlin
- driverId: UUID (PK, FK to user_profiles.userId)
- totalTrips: Int (default: 0)
- cancelledTrips: Int (default: 0)
- rating: Double (default: 5.0)
- earnings: Double (default: 0.0)
- updatedAt: Instant
```

**AuditLog** (`audit_logs` table)
```kotlin
- id: UUID (PK, auto-generated)
- entity: String (e.g., "UserProfile", "DriverDocument")
- entityId: String
- action: String (e.g., "UPDATE", "APPROVE", "REJECT")
- actor: UUID (user ID)
- metadata: String? (JSON, nullable)
- eventType: String
- createdAt: Instant
- success: Boolean (default: true)
- timestamp: Instant
- username: String (default: "Unknown")
```

#### DTOs
- `UpdateProfileRequest(name, phone, email)`
- `UserProfileResponse(id, userId, name, phone, email, photoUrl, role, status, createdAt, updatedAt)`
- `AddAddressRequest(label, lat, lng)`
- `RiderAddressResponse(id, riderId, label, lat, lng, createdAt)`
- `UploadDriverDocumentRequest(type, documentNumber, url)`
- `DriverDocumentResponse(id, driverId, type, documentNumber, url, status, reason, uploadedAt, reviewedBy)`

#### Events (Kafka)
- `ProfileUpdatedEvent(userId, name, email, phone, updatedAt)`
- `DriverDocumentUploadedEvent(driverId, documentId, type, uploadedAt)`
- `DriverApprovedEvent(driverId, approvedBy, approvedAt)`
- `DriverRejectedEvent(driverId, rejectedBy, reason, rejectedAt)`

### 6. Cross-Service Interactions

**Outbound**
- **Kafka** - Publishes events to `profile.updated`, `driver.document.uploaded`, `driver.approved`, `driver.rejected` topics

**Inbound**
- **Gateway → Profile (HTTP)**
  - All `/profile/**` requests proxied from Gateway
  - Expects `X-User-ID` header from Gateway

**Inter-Service Dependencies**
- Relies on Auth service for user authentication (via Gateway)
- Shares same PostgreSQL database (`ride_hailing`) with Auth service (different schemas/tables)

### 7. All External Dependencies

**Database**
- **PostgreSQL** (`postgres:5432`, database: `ride_hailing`)
- R2DBC driver: `r2dbc:postgresql://postgres:5432/ride_hailing`
- Credentials: `rh_user` / `rh_pass` (hardcoded, not Vault-managed)
- Migration: Flyway (baseline-on-migrate enabled)
- Connection pool: initial-size=5, max-size=20

**Cache**
- **Redis** (`redis:6379`)
- Currently NOT used by Profile service (configured but no usage found)

**Message Broker**
- **Kafka** (`kafka:9092`)
- Producer configuration:
  - Key serializer: `StringSerializer`
  - Value serializer: `JsonSerializer`
- Consumer configuration:
  - Group ID: `profile-service-group`
  - Auto offset reset: `earliest`
  - Key deserializer: `StringDeserializer`
  - Value deserializer: `JsonDeserializer`
  - Trusted packages: `*` (deserialize all)
- **Note:** No consumers implemented yet (only producers)

**Storage (Partially Implemented)**
- `StorageService` interface exists but implementation incomplete
- `generatePhotoUploadUrl()` returns placeholder URL (not integrated with S3/Cloud Storage)

**Build & Runtime**
- **Kotlin** - Primary language
- **Spring Boot 3.x** with WebFlux (Reactive)
- **R2DBC** - Reactive database driver
- **Gradle** - Build tool

### 8. All Places Where Auth is Enforced

**Gateway-Level (External)**
- All `/profile/**` requests validated by Gateway's `JwtAuth` filter
- Gateway injects `X-User-ID` header

**Service-Level (Internal)**
- **NO JWT validation** - Trusts Gateway's `X-User-ID` header
- **NO role-based access control** - Admin endpoints do NOT verify admin role
- **CRITICAL SECURITY GAP:** Any user with valid JWT can call admin endpoints if they bypass Gateway

**Data-Level Authorization**
- `AddressService.deleteAddress()` - Validates address belongs to `riderId`
- No other ownership checks (e.g., document uploads not validated for ownership)

### 9. All Rate-Limiters, Throttling, Filtering

**Rate Limiting**
- None at service level
- Relies on Gateway/Auth service for rate limiting

**Throttling**
- R2DBC connection pool (max-size: 20) limits concurrent database queries
- No application-level throttling

**Filtering**
- None

### 10. All Stateful Flows

#### Profile Creation/Update Flow
1. Client → Gateway → `PUT /profile/me` (headers: X-User-ID, body: {name, phone, email})
2. `ProfileService.updateProfile(userId, request)`
3. Check if profile exists for `userId`
4. If not exists: Create new `UserProfile` (role=RIDER, status=ACTIVE)
5. If exists: Update name, phone, email, set `updatedAt=now()`
6. Save to database (R2DBC reactive)
7. Publish `ProfileUpdatedEvent` to Kafka
8. Return `UserProfileResponse`

#### Photo Upload URL Generation Flow
1. Client → `POST /profile/me/photo` (headers: X-User-ID)
2. `ProfileService.generatePhotoUploadUrl(userId)`
3. **INCOMPLETE:** Returns hardcoded placeholder URL
4. Expected: Generate pre-signed S3 URL for user-specific path
5. Return `{uploadUrl: "https://placeholder.com/upload"}`

#### Address Management Flow
1. **Add Address**
   - Client → `POST /profile/rider/addresses` (body: {label, lat, lng})
   - Create `RiderAddress` (riderId=userId)
   - Save to database
   - Return `RiderAddressResponse`

2. **List Addresses**
   - Client → `GET /profile/rider/addresses`
   - Query: `SELECT * FROM rider_addresses WHERE rider_id = ?`
   - Return list of `RiderAddressResponse`

3. **Delete Address**
   - Client → `DELETE /profile/rider/addresses/{id}`
   - Validate: `SELECT * FROM rider_addresses WHERE id = ? AND rider_id = ?`
   - If not found or wrong owner: Throw error
   - Delete: `DELETE FROM rider_addresses WHERE id = ?`
   - Return 204 No Content

#### Driver Document Upload Flow
1. Client → `POST /profile/driver/documents` (body: {type, documentNumber, url})
2. Create `DriverDocument` (driverId=userId, status=PENDING)
3. Save to database
4. Publish `DriverDocumentUploadedEvent` to Kafka
5. Return `DriverDocumentResponse`

#### Driver Document Approval Flow
1. Admin → `POST /profile/admin/driver/{id}/approve` (headers: X-User-ID)
2. `AdminProfileService.approveDocument(adminId, documentId)`
3. Find document by ID
4. Update: `status=APPROVED`, `reviewedBy=adminId`
5. Publish `DriverApprovedEvent` to Kafka
6. Audit log: `APPROVE` action
7. Return `DriverDocumentResponse`

#### Driver Document Rejection Flow
1. Admin → `POST /profile/admin/driver/{id}/reject` (body: {reason})
2. `AdminProfileService.rejectDocument(adminId, documentId, reason)`
3. Find document by ID
4. Update: `status=REJECTED`, `reviewedBy=adminId`, `reason=reason`
5. Publish `DriverRejectedEvent` to Kafka
6. Audit log: `REJECT` action
7. Return `DriverDocumentResponse`

### 11. All Features Started but Not Finished

1. **Storage Integration** - `StorageService` interface defined but no implementation
   - `generatePhotoUploadUrl()` returns placeholder
   - No S3/Cloud Storage integration

2. **Driver Stats Tracking** - `DriverStats` entity exists but:
   - No endpoints to update stats
   - Not updated by any service logic
   - Likely intended for Trip service integration (not built yet)

3. **Profile Photo Upload** - Flow incomplete:
   - Pre-signed URL generation not implemented
   - No callback to update `photoUrl` after upload

4. **Kafka Consumers** - Consumer config exists but no listeners
   - Likely intended to consume events from other services (e.g., Trip service updates driver stats)

5. **Admin Role Enforcement** - Admin endpoints exist but no role validation
   - **CRITICAL GAP:** Non-admin users can call admin endpoints

6. **Email/Phone Verification** - No verification flow for email/phone changes

7. **Document Upload Validation** - No ownership check on document uploads
   - Any user can upload documents for any driver ID

### 12. All TODOs, FIXMEs, Unused Classes, Dead Code

**TODOs/FIXMEs**
- In `application.yml` comment: `# ✅ REQUIRED FIX` (already applied, comment can be removed)

**Incomplete Implementations**
1. `StorageService` - Interface with no concrete implementation
2. `DriverStats` - Entity with no business logic

**Unused Code**
- Redis dependency configured but never used

**Dead Code**
- None identified

### 13. All Config-Based Behaviors

**Environment Variables**
- None (all config in application.yml)

**Application Properties (application.yml)**
```yaml
server.port: 8080

spring.application.name: profile-service

spring.r2dbc:
  url: r2dbc:postgresql://postgres:5432/ride_hailing
  username: rh_user  # ⚠️ Hardcoded (not Vault-managed)
  password: rh_pass  # ⚠️ Hardcoded (not Vault-managed)
  pool:
    initial-size: 5
    max-size: 20

spring.flyway:
  url: jdbc:postgresql://postgres:5432/ride_hailing
  user: rh_user
  password: rh_pass
  locations: classpath:db/migration
  enabled: true
  baseline-on-migrate: true

spring.data.redis:
  host: redis
  port: 6379

spring.kafka:
  bootstrap-servers: kafka:9092
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  consumer:
    group-id: profile-service-group
    auto-offset-reset: earliest
    key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    properties:
      spring.json.trusted.packages: "*"  # ⚠️ Security risk (deserialize all classes)

logging.level:
  org.springframework.r2dbc: INFO
  org.flywaydb: INFO
  org.apache.kafka: INFO
```

**Hardcoded Values**
- Database credentials (`rh_user` / `rh_pass`)
- Kafka topics:
  - `profile.updated`
  - `driver.document.uploaded`
  - `driver.approved`
  - `driver.rejected`

**Feature Flags**
- None

---

## 🚨 CRITICAL RISKS & GAPS

### Security Risks

1. **Profile Service: Missing Admin Role Validation** ⚠️ **CRITICAL**
   - Admin endpoints (`/profile/admin/driver/*`) do NOT validate admin role
   - Any authenticated user can approve/reject driver documents if Gateway is bypassed
   - **Mitigation:** Add role check in `AdminController` or implement service-level authorization

2. **Profile Service: Hardcoded Database Credentials** ⚠️ **HIGH**
   - Database username/password in plaintext in `application.yml`
   - Not managed by Vault (unlike Auth service)
   - **Mitigation:** Integrate Spring Cloud Vault for Profile service

3. **Kafka: Trusted Packages = "*"** ⚠️ **HIGH**
   - Profile service allows deserialization of all classes
   - Exposes to deserialization attacks
   - **Mitigation:** Restrict to specific package: `com.rido.profile.event`

4. **Gateway: Actuator Endpoints Publicly Exposed** ⚠️ **MEDIUM**
   - `/actuator/**` accessible without authentication
   - Exposes sensitive runtime information
   - **Mitigation:** Add authentication or restrict to internal network only

5. **Auth Service: DebugController in Production** ⚠️ **MEDIUM**
   - `/auth/debug/unlock` active in `dev` and `test` profiles
   - If production runs with `test` profile: Accounts can be unlocked by anyone
   - **Mitigation:** Remove `test` from `@Profile annotation` or ensure production never uses test profile

6. **Profile Service: No Service-Level Authentication** ⚠️ **MEDIUM**
   - Trusts `X-User-ID` header implicitly
   - If Gateway is bypassed or misconfigured: Service accepts any user ID
   - **Mitigation:** Add mTLS between Gateway and Profile service

7. **Profile Service: Document Upload Ownership** ⚠️ **MEDIUM**
   - No validation that `driverId` in document upload matches authenticated user
   - User A can upload documents for User B
   - **Mitigation:** Add ownership check in `DriverDocumentService`

### Missing Features

8. **Session Limit Enforcement** ✅ **IMPLEMENTED (2025-12-04)**
   - `max-active-sessions: 5` configured and ENFORCED in `TokenService.java`
   - Users can now only have maximum configured sessions
   - **Status:** Fixed with audit logging and transaction safety

9. **Replay Protection** ⚠️ **LOW**
   - Device ID validation exists but no nonce-based replay detection
   - **Mitigation:** Implement nonce tracking in Redis

10. **Password Reset Flow** ⚠️ **LOW**
    - No password reset/recovery mechanism
    - Users locked out permanently if password forgotten
    - **Mitigation:** Implement password reset with email verification

### Architecture Gaps

11. **No Circuit Breakers** ⚠️ **MEDIUM**
    - Gateway has no resilience patterns
    - Single downstream failure can cascade
    - **Mitigation:** Integrate Resilience4j circuit breakers

12. **No Distributed Tracing** ⚠️ **LOW**
    - Difficult to debug cross-service requests
    - **Mitigation:** Integrate OpenTelemetry (partially configured in infra)

13. **Storage Service Incomplete** ⚠️ **MEDIUM**
    - Profile photo upload returns placeholder URLs
    - **Mitigation:** Implement S3/Cloud Storage integration

14. **Driver Stats Not Implemented** ⚠️ **LOW**
    - Database schema exists but no business logic
    - **Mitigation:** Implement trip completion events from Trip service (when built)

---

## 📊 SUMMARY STATISTICS

| Metric | Auth | Gateway | Profile | Total |
|--------|------|---------|---------|-------|
| **Java/Kotlin Files** | 54 | 10 | 16 | 80 |
| **Public Endpoints** | 15 | N/A | 8 | 23 |
| **Scheduled Tasks** | 2 | 2 | 0 | 4 |
| **Database Tables** | 3 | 0 | 5 | 8 |
| **External Dependencies** | 5 | 3 | 4 | 12 |
| **Security Filters** | 3 | 1 | 0 | 4 |
| **Rate Limiters** | 4 | 0 | 0 | 4 |
| **Kafka Topics** | 0 | 0 | 4 | 4 |
| **Critical Risks** | 2 | 1 | 4 | 7 |
| **Incomplete Features** | 5 | 3 | 7 | 15 |

---

## 🎯 RECOMMENDATIONS

### Immediate Actions (Critical)
1. Add admin role validation to Profile service admin endpoints
2. Move Profile service DB credentials to Vault
3. Restrict Kafka trusted packages to `com.rido.profile.event`
4. Secure Gateway actuator endpoints

### Short-Term (High Priority)
1. Implement session limit enforcement
2. Add mTLS between Gateway and Profile service
3. Implement document upload ownership validation
4. Integrate circuit breakers in Gateway
5. Complete Storage service implementation

### Long-Term (Medium Priority)
1. Implement password reset flow
2. Add distributed tracing (OpenTelemetry)
3. Implement Driver Stats business logic
4. Add email/phone verification
5. Implement service discovery (Consul/Eureka)

---

**End of Capability Map**
