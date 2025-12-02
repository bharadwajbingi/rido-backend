# 🧪 Rido Auth - Comprehensive Test Suite

This directory contains **12 modular test scripts** that generate comprehensive Postman collections for testing all aspects of the Rido authentication system.

## 📝 Test Scripts

### 1. **Registration Tests** (`01_registration_tests.py`)
- ✅ Valid registration
- ❌ Duplicate username
- ❌ Weak password (short, no special chars, common, all numbers)
- ❌ Missing fields (username, password, empty, null)
- ❌ Invalid username format (spaces, special chars, too long/short, unicode, emoji)
- 🛡️ SQL injection payloads (7 tests)
- 🛡️ XSS payloads (7 tests)
- ❌ Invalid body schema (8 tests)
- **Total: ~42 tests**

### 2. **Login Tests** (`02_login_tests.py`)
- ✅ Valid login
- ❌ Wrong password
- ❌ Wrong username
- 🔒 Locked account (after 5 failed attempts)
- ⏱️ Rate limiting (too many attempts)
- ❌ Invalid device-ID
- ❌ Malformed JSON
- 🛡️ SQL injection via login (7 tests)
- 🛡️ XSS via login (5 tests)
- ❌ Missing fields
- **Total: ~47 tests**

### 3. **Refresh Token Tests** (`03_refresh_token_tests.py`)
- ✅ Valid refresh (1st and 2nd rotation)
- ❌ Token replay (used twice)
- ❌ Expired refresh token
- ❌ Blacklisted JTI
- 🔍 Different IP or device
- ❌ Malformed refresh tokens (8 tests)
- ❌ Tampered refresh tokens
- ❌ Missing refresh token
- ❌ Malformed JSON body
- **Total: ~26 tests**

### 4. **Logout Tests** (`04_logout_tests.py`)
- ✅ Valid logout
- ✅ Logout twice (idempotent)
- ❌ Logout without token
- ❌ Logout with tampered access token
- ✅ Verify refresh token invalid after logout
- **Total: ~7 tests**

### 5. **JWKS & Signature Validation Tests** (`05_jwks_signature_tests.py`)
- ✅ Fetch JWKS
- ❌ Missing kid in JWT header
- ❌ Wrong kid in JWT header
- ❌ Wrong signature
- ❌ Expired JWT
- ❌ Invalid JWT header (wrong algorithm)
- **Total: ~8 tests**

### 6. **Access Token Validation Tests** (`06_access_token_tests.py`)
- ✅ Valid token → access allowed
- ❌ Missing token
- ❌ Expired token
- ❌ Tampered signature
- ❌ Wrong algorithm (HS256 instead of RS256)
- ❌ Missing claims
- ❌ Invalid claims
- **Total: ~9 tests**

### 7. **Roles & Authorization Tests** (`07_roles_authorization_tests.py`)
- ✅ Admin endpoint (valid admin)
- ❌ Admin endpoint (non-admin → forbidden)
- ✅ Public endpoints
- ❌ Missing roles in token
- **Total: ~7 tests**

### 8. **Rate Limit Tests** (`08_rate_limit_tests.py`)
- ✅ 5 login attempts → success
- ❌ 6th attempt → blocked (429)
- ⏰ Wait for cooldown
- ✅ 6th attempt after cooldown → allowed
- **Total: ~9 tests**

### 9. **Account Lockout Tests** (`09_account_lockout_tests.py`)
- ❌ 5 wrong passwords → lock account
- 🔒 Login after lock → blocked
- 🔓 Unlock (admin/internal)
- ✅ Login again → allowed
- **Total: ~10 tests**

### 10. **Session Management Tests** (`10_session_management_tests.py`)
- ✅ List active sessions
- ✅ Refresh creates new session
- ✅ Logout deletes session
- ✅ Delete specific JTI session
- ❌ Delete invalid session
- **Total: ~7 tests**

### 11. **Security Attack Tests** (`11_security_attack_tests.py`)
- 🛡️ SQL injection payloads (5 tests)
- 🛡️ XSS payloads (4 tests)
- 🛡️ NoSQL-like injections (3 tests)
- 🛡️ Null byte attacks
- 🛡️ Oversized JSON bodies
- 🛡️ Missing headers
- **Total: ~15 tests**

### 12. **mTLS & Internal Service Auth Tests** (`12_mtls_internal_auth_tests.py`)
- ✅ Gateway → Auth (valid mTLS)
- ❌ Service without certificate → fail
- ❌ Wrong certificate CN → fail
- 📋 Verify internal endpoints not publicly accessible
- **Total: ~4 tests**

---

## 🚀 Usage

### Generate All Test Collections

```bash
# Run all test generators
python testing-scripts/01_registration_tests.py
python testing-scripts/02_login_tests.py
python testing-scripts/03_refresh_token_tests.py
python testing-scripts/generate_remaining_tests.py  # Generates 04-12
```

### Import into Postman

1. Open Postman
2. Click **Import** → **File**
3. Select the generated `.json` files (e.g., `01_registration_tests.json`)
4. Run the collection

### Run All Tests Sequentially

```bash
# Use Newman (Postman CLI)
newman run 01_registration_tests.json
newman run 02_login_tests.json
newman run 03_refresh_token_tests.json
# ... and so on
```

---

## 📊 Summary

| **Script** | **Tests** | **Focus Area** |
|------------|-----------|----------------|
| 01 - Registration | ~42 | Input validation, security |
| 02 - Login | ~47 | Authentication, rate limiting |
| 03 - Refresh Token | ~26 | Token rotation, replay |
| 04 - Logout | ~7 | Session termination |
| 05 - JWKS | ~8 | Signature validation |
| 06 - Access Token | ~9 | JWT validation |
| 07 - Roles | ~7 | Authorization |
| 08 - Rate Limit | ~9 | Throttling |
| 09 - Account Lockout | ~10 | Security lockout |
| 10 - Session Management | ~7 | Session CRUD |
| 11 - Security Attacks | ~15 | Vulnerability testing |
| 12 - mTLS | ~4 | Internal service auth |
| **TOTAL** | **~191 tests** | **Complete coverage** |

---

## 🎯 Coverage

- ✅ **Authentication**: Registration, login, logout
- ✅ **Authorization**: Role-based access control
- ✅ **Token Management**: JWT, refresh tokens, rotation
- ✅ **Security**: SQL injection, XSS, NoSQL, rate limiting, lockouts
- ✅ **Session Management**: CRUD operations
- ✅ **Internal Auth**: mTLS, service-to-service
- ✅ **Edge Cases**: Malformed JSON, invalid formats, boundary conditions

---

## 🛠️ Environment Variables

Set these in Postman environment:

```json
{
  "base_url": "http://localhost:8080",
  "admin_user": "admin",
  "admin_password": "SuperSecretAdmin123"
}
```

---

## 🔒 Security Notes

- All scripts generate **unique usernames** using UUIDs to avoid conflicts
- **SQL injection** and **XSS** payloads are safely tested
- **Rate limiting** tests include delays to avoid false positives
- **Account lockout** tests use separate users to prevent blocking the main test user

---

## 📝 License

Part of the Rido Authentication System project.
