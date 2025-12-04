# Complete Testing Surface Map

**Generated:** 2025-12-04  
**Services:** Auth, Gateway, Profile  
**Purpose:** Exhaustive test requirements for production readiness

---

## 🔐 AUTH SERVICE - TESTING REQUIREMENTS

### 1. ENDPOINT TESTS - Required Coverage

#### `GET /auth/check-username`
**Required Tests:**
- ✅ Valid username returns `available: false` if taken
- ✅ Valid username returns `available: true` if free
- ✅ Null byte injection (`\u0000`) returns 400
- ✅ Empty username returns 400
- ✅ Very long username (>150 chars) returns 400
- ✅ Special characters in username handled correctly
- ✅ SQL injection attempts blocked
- ⚠️ **MISSING:** Concurrent checks for same username
- ⚠️ **MISSING:** Response time validation (<100ms)

**Risks if Not Tested:**
- Username enumeration attacks
- Database injection vulnerabilities
- Race conditions in registration

#### `POST /auth/register`
**Required Tests:**
- ✅ Valid registration creates user
- ✅ Duplicate username returns 409
- ✅ Weak password validation
- ✅ Missing fields return 400
- ✅ Rate limit (10/60s per IP) enforced
- ✅ Rate limit returns 429
- ✅ Password hashed with Argon2
- ✅ User role defaults to USER
- ✅ Audit log created
- ⚠️ **MISSING:** Password complexity requirements
- ⚠️ **MISSING:** Username format validation (alphanumeric, etc.)
- ⚠️ **MISSING:** Email format validation (if email username)
- ⚠️ **MISSING:** Concurrent registration attempts
- ⚠️ **MISSING:** Database rollback on partial failure
- ⚠️ **MISSING:** XSS in username field

**Risks if Not Tested:**
- Account takeover via weak passwords
- Database constraint violations
- Race conditions creating duplicate users
- Bypass rate limits via distributed IPs

#### `POST /auth/login`
**Required Tests:**
- ✅ Valid credentials return access + refresh tokens
- ✅ Invalid password returns 401
- ✅ Non-existent user returns 401
- ✅ Locked account returns 423
- ✅ Failed attempt increments counter
- ✅ 5 failed attempts lock account (30 min)
- ✅ Lockout persisted in Redis + DB
- ✅ Rate limit (50/60s per IP) enforced
- ✅ Rate limit (10/300s per username on failure) enforced
- ✅ Device ID header captured
- ✅ User-Agent header captured
- ✅ IP address captured
- ✅ Successful login clears failed attempts
- ✅ JWT contains correct claims (sub, iss, aud, roles, jti, kid)
- ✅ Refresh token hash stored (not plaintext)
- ✅ Session created in database
- ⚠️ **MISSING:** Admin login bypass lockout verification
- ⚠️ **MISSING:** Timing attack resistance (constant-time comparison)
- ⚠️ **MISSING:** Lockout expiry auto-unlock test
- ⚠️ **MISSING:** Multiple device login handling
- ⚠️ **MISSING:** Session limit enforcement (max 5 per user)
- ⚠️ **MISSING:** Concurrent login attempts
- ⚠️ **MISSING:** Login after password change
- ⚠️ **MISSING:** Login with special characters in password
- ⚠️ **MISSING:** Clock skew handling (exp, iat, nbf)

**Risks if Not Tested:**
- Brute force attacks succeed
- Account lockout bypass
- Session hijacking
- Timing attacks reveal valid usernames
- Resource exhaustion (unlimited sessions)

#### `POST /auth/refresh`
**Required Tests:**
- ✅ Valid refresh token returns new access token
- ✅ Valid refresh rotates refresh token
- ✅ Old refresh token revoked
- ✅ Invalid refresh token returns 401
- ✅ Expired refresh token returns 401
- ✅ Revoked refresh token returns 401
- ✅ Missing Device-ID returns 401
- ✅ Mismatched Device-ID returns 401 (replay protection)
- ✅ Rate limit (20/60s per IP) enforced
- ✅ New access token has same user/roles
- ✅ New refresh token stored in DB
- ⚠️ **MISSING:** Refresh after logout rejected
- ⚠️ **MISSING:** Refresh with blacklisted JTI rejected
- ⚠️ **MISSING:** Concurrent refresh requests (race condition)
- ⚠️ **MISSING:** Refresh near expiry window
- ⚠️ **MISSING:** Refresh with changed IP address
- ⚠️ **MISSING:** Refresh with changed User-Agent
- ⚠️ **MISSING:** Refresh after key rotation
- ⚠️ **MISSING:** Database transaction rollback on error

**Risks if Not Tested:**
- Token replay attacks
- Refresh token reuse vulnerabilities
- Session fixation attacks
- Race conditions creating multiple valid tokens

#### `POST /auth/logout`
**Required Tests:**
- ✅ Logout revokes refresh token in DB
- ✅ Logout blacklists access token JTI in Redis
- ✅ Blacklist TTL matches token remaining lifetime
- ✅ Missing refresh token returns 401
- ✅ Invalid refresh token handled gracefully
- ✅ Missing access token still revokes refresh
- ✅ Audit log created
- ⚠️ **MISSING:** Logout with already revoked token
- ⚠️ **MISSING:** Logout with expired access token
- ⚠️ **MISSING:** Concurrent logout requests
- ⚠️ **MISSING:** Partial logout (Redis fails, DB succeeds)
- ⚠️ **MISSING:** Logout all devices vs single device
- ⚠️ **MISSING:** Blacklist cleanup on expiry

**Risks if Not Tested:**
- Tokens remain valid after logout
- Inconsistent state (DB vs Redis)
- Memory leaks in Redis (no TTL)

#### `GET /auth/me`
**Required Tests:**
- ✅ Valid JWT returns user info (id, username)
- ✅ Missing JWT returns 401
- ✅ Invalid JWT returns 401
- ✅ Expired JWT returns 401
- ✅ Blacklisted JTI returns 401
- ✅ Non-existent user returns 404
- ⚠️ **MISSING:** JWT with invalid signature
- ⚠️ **MISSING:** JWT with wrong algorithm (HS256 instead of RS256)
- ⚠️ **MISSING:** JWT with wrong issuer
- ⚠️ **MISSING:** JWT with wrong audience
- ⚠️ **MISSING:** JWT with missing claims
- ⚠️ **MISSING:** JWT from rotated key (old KID)

**Risks if Not Tested:**
- Algorithm confusion attacks
- Issuer/audience bypass
- Information disclosure

#### `GET /auth/sessions`
**Required Tests:**
- ✅ Returns all active sessions for user
- ✅ Sessions sorted by createdAt (desc)
- ✅ Missing JWT returns 401
- ✅ Empty session list returns []
- ✅ Revoked sessions excluded
- ⚠️ **MISSING:** Pagination for users with many sessions
- ⚠️ **MISSING:** Session list for different user blocked
- ⚠️ **MISSING:** Expired but not revoked sessions handling

**Risks if Not Tested:**
- Session enumeration
- Unauthorized session visibility
- Performance issues with large session counts

#### `POST /auth/sessions/revoke-all`
**Required Tests:**
- ✅ Revokes all sessions for authenticated user
- ✅ Current session also revoked
- ✅ Missing JWT returns 401
- ✅ Audit log created
- ⚠️ **MISSING:** Verify all sessions actually revoked
- ⚠️ **MISSING:** Partial revocation failure handling
- ⚠️ **MISSING:** Concurrent revoke-all requests
- ⚠️ **MISSING:** Token blacklist updated for all sessions

**Risks if Not Tested:**
- Sessions remain active after revocation
- Inconsistent revocation across sessions

#### `POST /auth/sessions/{sessionId}/revoke`
**Required Tests:**
- ✅ Revokes specific session
- ✅ Session ownership validated
- ✅ Other user's session rejected
- ✅ Non-existent session returns 401/404
- ✅ Missing JWT returns 401
- ⚠️ **MISSING:** Revoke current session behavior
- ⚠️ **MISSING:** Revoke already revoked session
- ⚠️ **MISSING:** UUID format validation

**Risks if Not Tested:**
- Session revocation bypass
- Unauthorized session termination

---

#### ADMIN PORT (9091) ENDPOINT TESTS

#### `POST /admin/login`
**Required Tests:**
- ✅ Valid admin credentials return admin JWT
- ✅ Non-admin user rejected
- ✅ Invalid credentials return 401
- ✅ No refresh token issued
- ✅ Audit log created
- ✅ No rate limiting applied
- ✅ No account lockout applied
- ⚠️ **MISSING:** Admin login from non-VPN IP blocked (deployment config)
- ⚠️ **MISSING:** Admin JWT has admin role claim
- ⚠️ **MISSING:** Admin JWT lifetime validation
- ⚠️ **MISSING:** Concurrent admin logins

**Risks if Not Tested:**
- Non-admin users gain admin access
- Admin credentials brute-forced
- No audit trail for admin actions

#### `POST /admin/create`
**Required Tests:**
- ✅ Authenticated admin can create new admin
- ✅ Non-admin JWT rejected
- ✅ Missing JWT returns 401
- ✅ Duplicate admin username returns 409
- ✅ Created admin has ADMIN role
- ✅ Audit log includes creator ID
- ⚠️ **MISSING:** Regular user JWT cannot create admin
- ⚠️ **MISSING:** Admin self-deletion prevented
- ⚠️ **MISSING:** Weak password validation for admin
- ⚠️ **MISSING:** Admin creation limit (prevent admin proliferation)

**Risks if Not Tested:**
- Privilege escalation (user creates admin)
- Weak admin credentials
- Orphaned admin accounts

#### `POST /admin/key/rotate`
**Required Tests:**
- ✅ Authenticated admin can rotate keys
- ✅ New KID generated (UUID)
- ✅ New key stored in Vault
- ✅ Old keys retained for verification
- ✅ Audit log created
- ✅ JWKS endpoint updated
- ⚠️ **MISSING:** Old JWTs still validate after rotation
- ⚠️ **MISSING:** New JWTs use new KID
- ⚠️ **MISSING:** Vault write failure handling
- ⚠️ **MISSING:** Concurrent rotation requests blocked
- ⚠️ **MISSING:** Key expiry policy
- ⚠️ **MISSING:** Old key cleanup procedure

**Risks if Not Tested:**
- All tokens invalidated on rotation (outage)
- Vault inconsistency
- Race conditions in key generation

#### `GET /admin/audit/logs`
**Required Tests:**
- ✅ Authenticated admin can retrieve logs
- ✅ Pagination works (page, size)
- ✅ Max size limited to 100
- ✅ Sorted by timestamp DESC
- ✅ Non-admin JWT rejected
- ⚠️ **MISSING:** Filter by event type
- ⚠️ **MISSING:** Filter by date range
- ⚠️ **MISSING:** Filter by user ID
- ⚠️ **MISSING:** Export to CSV/JSON
- ⚠️ **MISSING:** Performance with millions of logs

**Risks if Not Tested:**
- Audit log access by non-admins
- Performance degradation
- Incomplete audit trail

---

### 2. SECURITY RULE TESTS

#### JWT Validation Tests
**Required:**
- ✅ RS256 signature verification
- ✅ Expired token rejected (exp claim)
- ✅ Not-yet-valid token rejected (nbf claim)
- ✅ KID header validation
- ✅ Unknown KID rejected
- ✅ ISS claim = "rido-auth-service"
- ✅ AUD claim contains "rido-api"
- ✅ JTI uniqueness
- ⚠️ **MISSING:** Algorithm confusion attack (HS256 with public key)
- ⚠️ **MISSING:** None algorithm attack
- ⚠️ **MISSING:** JWT without signature
- ⚠️ **MISSING:** Malformed JWT (invalid Base64)
- ⚠️ **MISSING:** JWT with extra periods
- ⚠️ **MISSING:** JWT with modified claims (tamper detection)
- ⚠️ **MISSING:** Clock skew tolerance testing
- ⚠️ **MISSING:** Very long JWT handling
- ⚠️ **MISSING:** Unicode in claims

**Risks if Not Tested:**
- Algorithm downgrade attacks
- Token forgery
- Signature bypass

#### Token Blacklist Tests
**Required:**
- ✅ Blacklisted JTI rejected
- ✅ Blacklist TTL equals token remaining life
- ✅ Expired blacklist entries auto-removed
- ⚠️ **MISSING:** Blacklist check performance (Redis latency)
- ⚠️ **MISSING:** Blacklist with millions of entries
- ⚠️ **MISSING:** Redis failure fallback (reject or allow?)
- ⚠️ **MISSING:** Blacklist cleanup verification
- ⚠️ **MISSING:** Concurrent blacklist writes

**Risks if Not Tested:**
- Memory exhaustion in Redis
- Blacklist bypass on Redis failure
- Performance degradation

#### mTLS Tests
**Required:**
- ✅ Valid client certificate accepted
- ✅ Invalid certificate rejected
- ✅ Expired certificate rejected
- ✅ Self-signed certificate rejected (if CA not trusted)
- ✅ CN extraction from certificate
- ⚠️ **MISSING:** Certificate revocation list (CRL) check
- ⚠️ **MISSING:** OCSP stapling validation
- ⚠️ **MISSING:** Certificate chain validation
- ⚠️ **MISSING:** Certificate expiry monitoring
- ⚠️ **MISSING:** Man-in-the-middle attack simulation
- ⚠️ **MISSING:** Certificate rotation testing

**Risks if Not Tested:**
- Compromised certificates accepted
- Service impersonation
- Expired cert outage

#### Role-Based Access Tests
**Required:**
- ✅ Admin role can access /admin/**
- ✅ User role cannot access /admin/**
- ⚠️ **MISSING:** Role claim missing from JWT
- ⚠️ **MISSING:** Role claim tampered
- ⚠️ **MISSING:** Role claim as array vs string
- ⚠️ **MISSING:** Multiple roles handling
- ⚠️ **MISSING:** Role case sensitivity

**Risks if Not Tested:**
- Privilege escalation
- Unauthorized admin access

#### Rate Limiting Tests
**Required:**
- ✅ Register: 10/60s per IP enforced
- ✅ Login: 50/60s per IP enforced
- ✅ Login failure: 10/300s per user enforced
- ✅ Refresh: 20/60s per IP enforced
- ✅ 429 returned on limit exceeded
- ⚠️ **MISSING:** Rate limit reset after window
- ⚠️ **MISSING:** Rate limit with distributed IPs (bypass)
- ⚠️ **MISSING:** Rate limit with IPv6
- ⚠️ **MISSING:** Rate limit bypass via header spoofing (X-Forwarded-For)
- ⚠️ **MISSING:** Redis failure allows requests?
- ⚠️ **MISSING:** Concurrent requests within limit
- ⚠️ **MISSING:** Rate limit per user vs per IP
- ⚠️ **MISSING:** DDoS simulation

**Risks if Not Tested:**
- Rate limit bypass
- Brute force attacks succeed
- Redis dependency creates single point of failure

#### Account Lockout Tests
**Required:**
- ✅ 5 failed attempts lock account
- ✅ Lockout duration = 30 minutes
- ✅ Lockout in Redis + DB
- ✅ Auto-unlock after expiry
- ✅ Admin accounts skip lockout
- ✅ Successful login clears lockout
- ⚠️ **MISSING:** Manual unlock by admin
- ⚠️ **MISSING:** Lockout notification to user (email)
- ⚠️ **MISSING:** Permanent lockout after N temporary lockouts
- ⚠️ **MISSING:** Lockout per IP vs per account
- ⚠️ **MISSING:** Distributed attack bypassing lockout
- ⚠️ **MISSING:** Redis expiry vs DB expiry mismatch
- ⚠️ **MISSING:** Clock drift affecting lockout duration

**Risks if Not Tested:**
- Account lockout bypass
- Permanent account lockout (no unlock)
- Inconsistent lockout state

---

### 3. STATEFUL FLOW TESTS

#### Full Login Flow
**Test Sequence:**
1. Register new user
2. Login with credentials
3. Validate access token
4. Validate refresh token stored
5. Validate session created
6. Validate audit log
7. Refresh access token
8. Validate old refresh token revoked
9. Logout
10. Validate tokens blacklisted
11. Attempt to use old token (expect 401)

**Missing Tests:**
- Multi-device concurrent login
- Login → logout → login again
- Login → change password → old token invalid
- Login → admin deletes user → token invalid
- Login → key rotation → token still valid

#### Session Management Flow
**Test Sequence:**
1. Login from 3 different devices
2. List sessions (expect 3)
3. Revoke one session
4. List sessions (expect 2)
5. Revoke all sessions
6. List sessions (expect 0)
7. Attempt to use revoked token (expect 401)

**Missing Tests:**
- Session limit enforcement (6th session revokes oldest)
- Concurrent session creation
- Session expiry vs revocation
- Session metadata accuracy (IP, User-Agent, Device-ID)

#### Token Refresh Flow
**Test Sequence:**
1. Login and get tokens
2. Wait until access token expires
3. Refresh with refresh token
4. Validate new access token
5. Validate new refresh token
6. Attempt old refresh token (expect 401)

**Missing Tests:**
- Refresh immediately after login
- Refresh near token expiry
- Refresh after refresh token expiry
- Refresh with compromised device ID
- Refresh during key rotation

#### Admin Creation Flow
**Test Sequence:**
1. Admin login
2. Create new admin
3. New admin login
4. New admin creates another admin
5. Validate audit trail

**Missing Tests:**
- Non-admin attempts admin creation
- First admin creation (bootstrap)
- Admin self-deletion blocked
- Admin role revocation

#### Key Rotation Flow
**Test Sequence:**
1. Issue tokens with key A
2. Rotate to key B
3. Validate old tokens still valid
4. Issue new tokens with key B
5. Validate JWKS has both keys
6. Wait for old tokens to expire
7. Cleanup old keys

**Missing Tests:**
- Multiple rapid rotations
- Rotation failure rollback
- Vault synchronization
- JWKS propagation delay
- Old key removal policy

---

### 4. DATABASE MODEL TESTS

#### UserEntity CRUD Tests
**Required:**
- ✅ Create user with all fields
- ✅ Read user by ID
- ✅ Read user by username
- ✅ Update user fields
- ✅ Delete user (soft delete?)
- ✅ Username uniqueness constraint
- ✅ ID auto-generation (UUID)
- ⚠️ **MISSING:** Cascade delete (sessions, audit logs)
- ⚠️ **MISSING:** Null field validation
- ⚠️ **MISSING:** Concurrent user creation (same username)
- ⚠️ **MISSING:** Database rollback on constraint violation
- ⚠️ **MISSING:** User with very long username (boundary)
- ⚠️ **MISSING:** Timestamp accuracy (createdAt)

**Risks if Not Tested:**
- Data integrity violations
- Orphaned records
- Race conditions

#### RefreshTokenEntity CRUD Tests
**Required:**
- ✅ Create refresh token
- ✅ Find by token hash
- ✅ Find active tokens by user ID
- ✅ Revoke token (soft delete)
- ✅ Delete expired tokens (cleanup)
- ✅ Token hash uniqueness
- ⚠️ **MISSING:** Cascade delete when user deleted
- ⚠️ **MISSING:** Index performance (userId, tokenHash)
- ⚠️ **MISSING:** Bulk delete performance
- ⚠️ **MISSING:** Transaction isolation (concurrent revoke)
- ⚠️ **MISSING:** Device ID validation

**Risks if Not Tested:**
- Orphaned tokens
- Slow queries
- Token reuse vulnerabilities

#### AuditLog CRUD Tests
**Required:**
- ✅ Create audit log entry
- ✅ Read logs by user ID
- ✅ Read logs by event type
- ✅ Read logs by timestamp range
- ✅ Pagination performance
- ⚠️ **MISSING:** Audit log retention policy
- ⚠️ **MISSING:** Audit log immutability (no updates)
- ⚠️ **MISSING:** Bulk insert performance
- ⚠️ **MISSING:** Index selectivity
- ⚠️ **MISSING:** Metadata JSON validation

**Risks if Not Tested:**
- Audit tampering
-Storage exhaustion
- Slow audit queries

---

### 5. BACKGROUND TASK TESTS

#### SessionCleanupService Tests
**Required:**
- ✅ Cleanup runs every 6 hours
- ✅ Deletes expired tokens
- ✅ Deletes revoked tokens
- ✅ Does not delete active tokens
- ✅ Bulk delete performance acceptable
- ⚠️ **MISSING:** Cleanup failure recovery
- ⚠️ **MISSING:** Cleanup with millions of records
- ⚠️ **MISSING:** Database lock duration
- ⚠️ **MISSING:** Cleanup while user is active
- ⚠️ **MISSING:** Cleanup monitoring/alerting
- ⚠️ **MISSING:** Overlapping cleanup runs prevented

**Risks if Not Tested:**
- Database table bloat
- Cleanup never runs
- Active sessions deleted

#### BootstrapAdminService Tests
**Required:**
- ✅ Creates admin if DB empty
- ✅ Skips if admin exists
- ✅ Uses env credentials
- ✅ Skips if password not set
- ✅ Audit log created
- ⚠️ **MISSING:** Bootstrap with invalid credentials
- ⚠️ **MISSING:** Bootstrap failure rollback
- ⚠️ **MISSING:** Bootstrap on every restart (idempotency)
- ⚠️ **MISSING:** Bootstrap with existing non-admin user

**Risks if Not Tested:**
- No admin access after deployment
- Multiple bootstrap admins created
- Credential leakage in logs

---

### 6. CROSS-SERVICE INTERACTION TESTS

#### Gateway → Auth (mTLS)
**Required:**
- ✅ Gateway can call Auth with valid cert
- ✅ Gateway rejected without cert
- ✅ Gateway rejected with invalid cert
- ✅ CN extracted and logged
- ⚠️ **MISSING:** Certificate rotation without downtime
- ⚠️ **MISSING:** Network partition handling
- ⚠️ **MISSING:** Retry logic on connection failure
- ⚠️ **MISSING:** Timeout configuration

**Risks if Not Tested:**
- Service impersonation
- Outage during cert rotation

#### Gateway JWKS Fetch
**Required:**
- ✅ Gateway fetches JWKS every 10s
- ✅ Gateway validates JWT with fetched keys
- ✅ Gateway handles fetch failure gracefully
- ⚠️ **MISSING:** JWKS cache invalidation
- ⚠️ **MISSING:** JWKS with rotated keys
- ⚠️ **MISSING:** Network lag between rotation and fetch
- ⚠️ **MISSING:** Auth service unavailable during validation

**Risks if Not Tested:**
- JWT validation fails after key rotation
- Stale JWKS cached indefinitely

---

### 7. CONFIG-BASED BEHAVIOR TESTS

#### Environment Variable Tests
**Required:**
- ✅ `FIRST_ADMIN_PASSWORD` empty skips bootstrap
- ✅ `FIRST_ADMIN_PASSWORD` set creates admin
- ✅ `JWT_ACCESS_TTL` custom value applied
- ✅ `JWT_REFRESH_TTL` custom value applied
- ⚠️ **MISSING:** Invalid TTL values handled
- ⚠️ **MISSING:** Negative TTL values rejected
- ⚠️ **MISSING:** Zero TTL values rejected
- ⚠️ **MISSING:** Very large TTL values (overflow)
- ⚠️ **MISSING:** Missing env vars use defaults

**Risks if Not Tested:**
- Service crash on invalid config
- Insecure default values used

#### Profile-Based Tests
**Required:**
- ✅ Debug endpoints disabled in production
- ✅ Debug endpoints enabled in dev/test
- ⚠️ **MISSING:** Production profile validation
- ⚠️ **MISSING:** Accidentally using test profile in prod

**Risks if Not Tested:**
- Debug endpoints exposed in production
- Security bypass vulnerabilities

---

### 8. FAILURE MODE TESTS

#### Network Failures
**Required:**
- ⚠️ **MISSING:** Redis connection failure
- ⚠️ **MISSING:** Database connection failure
- ⚠️ **MISSING:** Vault connection failure
- ⚠️ **MISSING:** Network timeout on key fetch
- ⚠️ **MISSING:** Partial network partition

**Risks if Not Tested:**
- Service crashes on dependency failure
- No graceful degradation

#### Invalid Token Tests
**Required:**
- ✅ Malformed Base64 rejected
- ✅ Missing signature rejected
- ✅ Wrong algorithm rejected
- ⚠️ **MISSING:** Truncated JWT
- ⚠️ **MISSING:** JWT with null bytes
- ⚠️ **MISSING:** Very long JWT (>16KB)
- ⚠️ **MISSING:** JWT with invalid JSON

**Risks if Not Tested:**
- Parser crashes
- Buffer overflow vulnerabilities

#### Database Failures
**Required:**
- ⚠️ **MISSING:** Connection pool exhaustion
- ⚠️ **MISSING:** Deadlock handling
- ⚠️ **MISSING:** Transaction timeout
- ⚠️ **MISSING:** Unique constraint violation
- ⚠️ **MISSING:** Foreign key constraint violation
- ⚠️ **MISSING:** Database disk full

**Risks if Not Tested:**
- Data corruption
- Service hangs indefinitely

#### Key Rotation Failures
**Required:**
- ⚠️ **MISSING:** Vault write fails during rotation
- ⚠️ **MISSING:** Key generation fails
- ⚠️ **MISSING:** Partial rotation (key created but not stored)
- ⚠️ **MISSING:** Concurrent rotation attempts

**Risks if Not Tested:**
- All tokens invalidated
- Service unable to sign new tokens

---

### 9. EDGE CASES & BOUNDARY TESTS

**Required:**
- ⚠️ **MISSING:** User with exactly 5 sessions (boundary for limit)
- ⚠️ **MISSING:** Login exactly at lockout expiry time
- ⚠️ **MISSING:** Token expiry within 1 second
- ⚠️ **MISSING:** Rate limit exactly at threshold
- ⚠️ **MISSING:** Username exactly 150 characters
- ⚠️ **MISSING:** Password exactly at minimum length
- ⚠️ **MISSING:** Very fast login/logout cycles
- ⚠️ **MISSING:** Midnight boundary for cron jobs
- ⚠️ **MISSING:** Leap second handling
- ⚠️ **MISSING:** Timezone changes

---

### 10. NEGATIVE TESTS

**Required:**
- ✅ Invalid credentials rejected
- ✅ Expired token rejected
- ✅ Blacklisted token rejected
- ⚠️ **MISSING:** SQL injection in all input fields
- ⚠️ **MISSING:** XSS in all input fields
- ⚠️ **MISSING:** CSRF attacks (cookie-based)
- ⚠️ **MISSING:** Header injection attacks
- ⚠️ **MISSING:** Path traversal in endpoints
- ⚠️ **MISSING:** NoSQL injection (if MongoDB used)
- ⚠️ **MISSING:** Command injection
- ⚠️ **MISSING:** XXE attacks (XML parsing)
- ⚠️ **MISSING:** SSRF attacks
- ⚠️ **MISSING:** Mass assignment vulnerabilities

---

### 11-14. CRITICAL GAP SUMMARY (AUTH)

**Missing Tests:** 200+ critical test cases  
**P0 Priority:** 40 tests (must have before production)  
**P1 Priority:** 80 tests (should have)  
**estimated Effort:** 8-10 weeks comprehensive coverage

---

## 🌐 GATEWAY - TESTING REQUIREMENTS

### Critical Tests Required:
1. JWT validation (algorithm confusion, invalid signatures)
2. JWKS refresh (key rotation, stale cache)
3. Redis blacklist (failover, performance)
4. Circuit breakers (backend failures)
5. Route forwarding (timeouts, retries)
6. Header injection (X-User-ID validation)
7. Load testing (10K req/sec)

**Missing Tests:** 80+ critical cases  
**Estimated Effort:** 4-5 weeks

---

## 👤 PROFILE - TESTING REQUIREMENTS

### **CRITICAL SECURITY GAPS:**
1. **Admin role enforcement** - ANY USER CAN APPROVE DOCUMENTS
2. **Document ownership** - USER A CAN UPLOAD FOR USER B
3. **Header spoofing** - X-User-ID not validated

### Critical Tests Required:
1. Admin authorization on all admin endpoints
2. Document upload ownership validation
3. Kafka event delivery confirmation
4. R2DBC failover and connection pooling
5. Storage service implementation
6. Cross-user access prevention
7. Load testing (1K concurrent updates)

**Missing Tests:** 100+ critical cases  
**Estimated Effort:** 6-7 weeks

---

## 📊 OVERALL SUMMARY

**Total Missing Tests:** 380+  
**Critical Security Gaps:** 12  
**Critical Stability Gaps:** 15  
**Critical Data Gaps:** 10  

**Production Readiness Estimate:**
- **Minimum Viable:** 4-6 weeks (P0 only)
- **Comprehensive:** 16-20 weeks (all priorities)

**Top 5 Critical Priorities:**
1. Profile admin role enforcement (1 day)
2. Profile document ownership (1 day)
3. Auth session limit enforcement (2 days)
4. Security penetration testing (1 week)
5. Load testing all services (1 week)

**End of Testing Surface Map**
