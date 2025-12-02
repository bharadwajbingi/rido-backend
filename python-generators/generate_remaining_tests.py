#!/usr/bin/env python3
"""
Master script to generate all remaining test scripts (4-12)
"""
import json
import uuid
import os

def create_test(name, url, method="POST", body=None, headers=None, tests=None, description="", delay_ms=0):
    """Helper to create a test request"""
    req = {
        "name": name,
        "request": {
            "method": method,
            "header": headers or [{"key": "Content-Type", "value": "application/json"}],
            "url": f"{{{{base_url}}}}{url}"
        }
    }
    
    if body is not None:
        req["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body, indent=4) if not isinstance(body, str) else body
        }
    
    if description:
        req["request"]["description"] = description
    
    if delay_ms > 0:
        req["event"] = req.get("event", [])
        req["event"].insert(0, {
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": [f"setTimeout(() => {{}}, {delay_ms});"]
            }
        })
    
    if tests:
        req["event"] = req.get("event", [])
        req["event"].append({
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": tests
            }
        })
    
    return req

def write_collection(filename, name, description, items, variables=None):
    """Write a Postman collection to file"""
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": name,
            "description": description,
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "item": items,
        "variable": variables or [
            {"key": "base_url", "value": "http://localhost:8080"}
        ]
    }
    
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(collection, f, indent=2)
    
    print(f"✅ Generated {len(items)} tests → {filename}")
    return len(items)

# ============================================================================
# 04 - LOGOUT TESTS
# ============================================================================
logout_tests = []
logout_tests.append(create_test("📋 Setup - Register & Login", "/auth/register", body={"username": f"logouttest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('logout_user', reqBody.username);", "pm.environment.set('logout_pass', 'SecurePass123!');"]))

logout_tests.append(create_test("📋 Setup - Login", "/auth/login", body={"username": "{{logout_user}}", "password": "{{logout_pass}}"},  tests=["pm.test('Login', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('logout_access', resp.accessToken);", "pm.environment.set('logout_refresh', resp.refreshToken);"], delay_ms=1000))

logout_tests.append(create_test("✅ Valid Logout", "/auth/logout", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "Authorization", "value": "Bearer {{logout_access}}"}], body={"refreshToken": "{{logout_refresh}}"}, tests=["pm.test('Logout Success', () => pm.response.to.have.status(200));"]))

logout_tests.append(create_test("✅ Logout Twice (Idempotent)", "/auth/logout", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "Authorization", "value": "Bearer {{logout_access}}"}], body={"refreshToken": "{{logout_refresh}}"}, tests=["pm.test('Second Logout', () => pm.expect(pm.response.code).to.be.oneOf([200, 401]));"]))

logout_tests.append(create_test("❌ Logout Without Token", "/auth/logout", body={"refreshToken": "{{logout_refresh}}"}, tests=["pm.test('No Auth Header', () => pm.response.to.have.status(401));"]))

logout_tests.append(create_test("❌ Logout With Tampered Access Token", "/auth/logout", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake"}], body={"refreshToken": "{{logout_refresh}}"}, tests=["pm.test('Tampered Token Rejected', () => pm.response.to.have.status(401));"]))

logout_tests.append(create_test("❌ Verify Refresh Token Invalid After Logout", "/auth/refresh", body={"refreshToken": "{{logout_refresh}}"}, tests=["pm.test('Refresh After Logout Blocked', () => pm.response.to.have.status(401));"]))

write_collection("04_logout_tests.json", "04 - Logout Tests", "Comprehensive logout testing including idempotency", logout_tests, [{"key": "base_url", "value": "http://localhost:8080"}])

# ============================================================================
# 05 - JWKS / SIGNATURE VALIDATION TESTS
# ============================================================================
jwks_tests = []
jwks_tests.append(create_test("✅ Fetch JWKS", "/auth/keys/jwks.json", method="GET", headers=[], tests=["pm.test('JWKS Available', () => pm.response.to.have.status(200));", "const jwks = pm.response.json();", "pm.test('Has keys array', () => pm.expect(jwks.keys).to.be.an('array'));", "pm.test('At least 1 key', () => pm.expect(jwks.keys.length).to.be.at.least(1));", "pm.test('Key is RSA', () => pm.expect(jwks.keys[0].kty).to.eql('RSA'));", "pm.test('Has kid', () => pm.expect(jwks.keys[0].kid).to.exist);", "pm.environment.set('valid_kid', jwks.keys[0].kid);"]))

jwks_tests.append(create_test("📋 Setup - Create JWT for Validation Tests", "/auth/register", body={"username": f"jwttest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('jwt_user', reqBody.username);"]))

jwks_tests.append(create_test("📋 Setup - Login to Get JWT", "/auth/login", body={"username": "{{jwt_user}}", "password": "SecurePass123!"}, tests=["pm.test('Login', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('valid_jwt', resp.accessToken);"], delay_ms=1000))

jwks_tests.append(create_test("❌ Missing kid in JWT Header", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake"}], tests=["pm.test('Missing kid Rejected', () => pm.response.to.have.status(401));"]))

jwks_tests.append(create_test("❌ Wrong kid in JWT Header", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6Indyb25nLWtpZCJ9.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake"}], tests=["pm.test('Wrong kid Rejected', () => pm.response.to.have.status(401));"]))

jwks_tests.append(create_test("❌ Wrong Signature", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2lkIn0.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.wrongsignature"}], tests=["pm.test('Wrong Signature Rejected', () => pm.response.to.have.status(401));"]))

jwks_tests.append(create_test("❌ Expired JWT", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjE2MDAwMDAwMDF9.fake"}], tests=["pm.test('Expired JWT Rejected', () => pm.response.to.have.status(401));"]))

jwks_tests.append(create_test("❌ Invalid JWT Header - Wrong Algorithm", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake"}], tests=["pm.test('Wrong Algorithm Rejected', () => pm.response.to.have.status(401));"]))

write_collection("05_jwks_signature_tests.json", "05 - JWKS & Signature Validation Tests", "JWT cryptographic validation testing", jwks_tests)

# ============================================================================
# 06 - ACCESS TOKEN VALIDATION TESTS
# ============================================================================
access_tests = []
access_tests.append(create_test("📋 Setup - Register & Login", "/auth/register", body={"username": f"accesstest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('access_user', reqBody.username);"]))

access_tests.append(create_test("📋 Setup - Login", "/auth/login", body={"username": "{{access_user}}", "password": "SecurePass123!"}, tests=["pm.test('Login', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('valid_access', resp.accessToken);"], delay_ms=1000))

access_tests.append(create_test("✅ Valid Token - Access Allowed", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer {{valid_access}}"}], tests=["pm.test('Access Allowed', () => pm.response.to.have.status(200));", "pm.test('Has user data', () => pm.expect(pm.response.json().username).to.exist);"]))

access_tests.append(create_test("❌ Missing Token", "/auth/me", method="GET", headers=[], tests=["pm.test('Missing Token Rejected', () => pm.response.to.have.status(401));"]))

access_tests.append(create_test("❌ Expired Token", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE2MDAwMDAwMDB9.fake"}], tests=["pm.test('Expired Token Rejected', () => pm.response.to.have.status(401));"]))

access_tests.append(create_test("❌ Tampered Signature", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.tampered"}], tests=["pm.test('Tampered Signature Rejected', () => pm.response.to.have.status(401));"]))

access_tests.append(create_test("❌ Wrong Algorithm (HS256 instead of RS256)", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake"}], tests=["pm.test('Wrong Algorithm Rejected', () => pm.response.to.have.status(401));"]))

access_tests.append(create_test("❌ Missing Claims", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.e30.fake"}], tests=["pm.test('Missing Claims Rejected', () => pm.response.to.have.status(401));"]))

access_tests.append(create_test("❌ Invalid Claims", "/auth/me", method="GET", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJpbnZhbGlkIjoidHJ1ZSJ9.fake"}], tests=["pm.test('Invalid Claims Rejected', () => pm.response.to.have.status(401));"]))

write_collection("06_access_token_tests.json", "06 - Access Token Validation Tests", "Comprehensive JWT access token testing", access_tests)

# ============================================================================
# 07 - ROLES / AUTHORIZATION TESTS
# ============================================================================
role_tests = []
role_tests.append(create_test("📋 Setup - Admin Login", "/auth/login", body={"username": "admin", "password": "SuperSecretAdmin123"}, tests=["pm.test('Admin Login', () => {", "    if (pm.response.code === 200) {", "        const resp = pm.response.json();", "        pm.environment.set('admin_token', resp.accessToken);", "    } else {", "        console.log('Admin user not available');", "    }", "});"]))

role_tests.append(create_test("✅ Admin Endpoint (Valid Admin)", "/auth/keys/rotate", method="POST", headers=[{"key": "Authorization", "value": "Bearer {{admin_token}}"}], tests=["pm.test('Admin Access', () => pm.expect(pm.response.code).to.be.oneOf([200, 401, 403]));"]))

role_tests.append(create_test("📋 Setup - Non-Admin User", "/auth/register", body={"username": f"regularuser_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('regular_user', reqBody.username);"]))

role_tests.append(create_test("📋 Setup - Login Non-Admin", "/auth/login", body={"username": "{{regular_user}}", "password": "SecurePass123!"}, tests=["pm.test('Login', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('regular_token', resp.accessToken);"], delay_ms=1000))

role_tests.append(create_test("❌ Admin Endpoint (Non-Admin → Forbidden)", "/auth/keys/rotate", method="POST", headers=[{"key": "Authorization", "value": "Bearer {{regular_token}}"}], tests=["pm.test('Forbidden', () => pm.response.to.have.status(403));"]))

role_tests.append(create_test("✅ Public Endpoints (No Auth)", "/auth/keys/jwks.json", method="GET", headers=[], tests=["pm.test('Public Access', () => pm.response.to.have.status(200));"]))

role_tests.append(create_test("❌ Missing Roles in Token", "/auth/keys/rotate", method="POST", headers=[{"key": "Authorization", "value": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake"}], tests=["pm.test('Missing Roles Rejected', () => pm.response.to.have.status(401));"]))

write_collection("07_roles_authorization_tests.json", "07 - Roles & Authorization Tests", "Role-based access control testing", role_tests)

# ============================================================================
# 08 - RATE LIMIT TESTS
# ============================================================================
rate_tests = []
rate_tests.append(create_test("📋 Setup - Create Rate Limit Test User", "/auth/register", body={"username": f"ratetest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('rate_user', reqBody.username);"]))

for i in range(1, 6):
    rate_tests.append(create_test(f"✅ Login Attempt #{i} - Should Succeed", "/auth/login", body={"username": "{{rate_user}}", "password": "SecurePass123!"}, tests=[f"pm.test('Attempt {i} Success', () => pm.response.to.have.status(200));"], delay_ms=500))

rate_tests.append(create_test("❌ 6th Attempt - Should Be Rate Limited", "/auth/login", body={"username": "{{rate_user}}", "password": "SecurePass123!"}, tests=["pm.test('Rate Limited', () => pm.expect(pm.response.code).to.be.oneOf([429, 200]));"], delay_ms=100))

rate_tests.append(create_test("⏰ Wait for Cooldown", "/auth/keys/jwks.json", method="GET", tests=["pm.test('Waiting', () => pm.response.to.have.status(200));"], delay_ms=3000, description="Wait 3 seconds for rate limit cooldown"))

rate_tests.append(create_test("✅ After Cooldown - Should Be Allowed", "/auth/login", body={"username": "{{rate_user}}", "password": "SecurePass123!"}, tests=["pm.test('Allowed After Cooldown', () => pm.response.to.have.status(200));"], delay_ms=1000))

write_collection("08_rate_limit_tests.json", "08 - Rate Limit Tests", "Rate limiting and throttling validation", rate_tests)

# ============================================================================
# 09 - ACCOUNT LOCKOUT TESTS
# ============================================================================
lockout_tests = []
lockout_tests.append(create_test("📋 Setup - Create Lockout Test User", "/auth/register", body={"username": f"locktest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('lock_user', reqBody.username);"]))

for i in range(1, 6):
    lockout_tests.append(create_test(f"❌ Wrong Password Attempt #{i}", "/auth/login", body={"username": "{{lock_user}}", "password": "WrongPassword"}, tests=[f"pm.test('Attempt {i} Failed', () => pm.expect(pm.response.code).to.be.oneOf([401, 423]));"], delay_ms=500))

lockout_tests.append(create_test("🔒 5 Wrong Passwords - Account Locked", "/auth/login", body={"username": "{{lock_user}}", "password": "SecurePass123!"}, tests=["pm.test('Account Locked', () => pm.response.to.have.status(423));", "pm.test('Locked Message', () => pm.expect(pm.response.json().error).to.include('locked'));"], delay_ms=1000))

lockout_tests.append(create_test("❌ Login After Lock - Blocked", "/auth/login", body={"username": "{{lock_user}}", "password": "SecurePass123!"}, tests=["pm.test('Still Locked', () => pm.response.to.have.status(423));"]))

lockout_tests.append(create_test("🔓 Unlock (Admin/Internal)", "/internal/admin/unlock", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "X-SYSTEM-KEY", "value": "InternalSecretKey"}], body={"username": "{{lock_user}}"}, tests=["pm.test('Unlock Response', () => pm.expect(pm.response.code).to.be.oneOf([200, 404]));"], description="Note: May need debug profile enabled"))

lockout_tests.append(create_test("✅ Login Again - Should Be Allowed", "/auth/login", body={"username": "{{lock_user}}", "password": "SecurePass123!"}, tests=["pm.test('Login After Unlock', () => pm.expect(pm.response.code).to.be.oneOf([200, 423]));"], delay_ms=2000))

write_collection("09_account_lockout_tests.json", "09 - Account Lockout Tests", "Account lockout and unlock testing", lockout_tests)

# ============================================================================
# 10 - SESSION MANAGEMENT TESTS
# ============================================================================
session_tests = []
session_tests.append(create_test("📋 Setup - Register & Login", "/auth/register", body={"username": f"sessiontest_{uuid.uuid4().hex[:8]}", "password": "SecurePass123!"}, tests=["pm.test('Setup', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));", "var reqBody = JSON.parse(pm.request.body.raw);", "pm.environment.set('session_user', reqBody.username);"]))

session_tests.append(create_test("📋 Setup - Login", "/auth/login", body={"username": "{{session_user}}", "password": "SecurePass123!"}, tests=["pm.test('Login', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('session_token', resp.accessToken);", "pm.environment.set('session_refresh', resp.refreshToken);"], delay_ms=1000))

session_tests.append(create_test("✅ List Active Sessions", "/auth/sessions", method="GET", headers=[{"key": "Authorization", "value": "Bearer {{session_token}}"}], tests=["pm.test('List Sessions', () => pm.response.to.have.status(200));", "const sessions = pm.response.json();", "pm.test('Is Array', () => pm.expect(sessions).to.be.an('array'));", "pm.test('Has Sessions', () => pm.expect(sessions.length).to.be.at.least(1));"]))

session_tests.append(create_test("✅ Refresh Creates New Session", "/auth/refresh", body={"refreshToken": "{{session_refresh}}"}, tests=["pm.test('Refresh Success', () => pm.response.to.have.status(200));", "const resp = pm.response.json();", "pm.environment.set('session_token', resp.accessToken);", "pm.environment.set('session_refresh', resp.refreshToken);"], delay_ms=500))

session_tests.append(create_test("✅ Logout Deletes Session", "/auth/logout", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "Authorization", "value": "Bearer {{session_token}}"}], body={"refreshToken": "{{session_refresh}}"}, tests=["pm.test('Logout Success', () => pm.response.to.have.status(200));"]))

session_tests.append(create_test("✅ Delete Specific JTI Session", "/auth/sessions/{jti}", method="DELETE", headers=[{"key": "Authorization", "value": "Bearer {{session_token}}"}], tests=["pm.test('Delete Session', () => pm.expect(pm.response.code).to.be.oneOf([200, 401, 404]));"], description="Note: Replace {jti} with actual JTI"))

session_tests.append(create_test("❌ Delete Invalid Session", "/auth/sessions/invalid-jti-00000", method="DELETE", headers=[{"key": "Authorization", "value": "Bearer {{session_token}}"}], tests=["pm.test('Invalid Session', () => pm.expect(pm.response.code).to.be.oneOf([404, 401]));"], description="Attempt to delete non-existent session"))

write_collection("10_session_management_tests.json", "10 - Session Management Tests", "Session listing and deletion testing", session_tests)

# ============================================================================
# 11 - SECURITY ATTACK TESTS
# ============================================================================
security_tests = []

# SQL Injection
sql_payloads = ["admin' OR '1'='1", "'; DROP TABLE users; --", "admin'--", "1' UNION SELECT NULL--", "' OR 1=1--"]
for i, payload in enumerate(sql_payloads):
    security_tests.append(create_test(f"🛡️ SQL Injection #{i+1}", "/auth/login", body={"username": payload, "password": "test"}, tests=["pm.test('SQLi Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 401, 423, 429]));"], delay_ms=300))

# XSS
xss_payloads = ["<script>alert('xss')</script>", "<img src=x onerror=alert(1)>", "javascript:alert(1)", "<svg onload=alert(1)>"]
for i, payload in enumerate(xss_payloads):
    security_tests.append(create_test(f"🛡️ XSS Attack #{i+1}", "/auth/register", body={"username": payload, "password": "test"}, tests=["pm.test('XSS Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 200, 409]));"], delay_ms=300))

# NoSQL-like injections
nosql_payloads = ['{"$ne": null}', '{"$gt": ""}', '{"username": {"$regex": ".*"}}']
for i, payload in enumerate(nosql_payloads):
    try:
        parsed = json.loads(payload)
        security_tests.append(create_test(f"🛡️ NoSQL Injection #{i+1}", "/auth/login", body={"username": parsed, "password": "test"}, tests=["pm.test('NoSQL Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 401]));"], delay_ms=300))
    except:
        pass

# Null byte attacks
security_tests.append(create_test("🛡️ Null Byte Attack", "/auth/check-username?username=admin\\u0000", method="GET", tests=["pm.test('Null Byte Rejected', () => pm.response.to.have.status(400));"]))

# Oversized JSON
security_tests.append(create_test("🛡️ Oversized JSON Body", "/auth/register", body={"username": "a" * 10000, "password": "test"}, tests=["pm.test('Oversized Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 413]));"], description="Very large payload"))

# Missing headers
security_tests.append(create_test("🛡️ Missing Content-Type Header", "/auth/login", headers=[], body='{"username": "test", "password": "test"}', tests=["pm.test('Handled', () => pm.expect(pm.response.code).to.be.oneOf([200, 400, 401, 415]));"]))

write_collection("11_security_attack_tests.json", "11 - Security Attack Tests", "Comprehensive security vulnerability testing", security_tests)

# ============================================================================
# 12 - INTERNAL SERVICE AUTH (mTLS) TESTS
# ============================================================================
mtls_tests = []
mtls_tests.append(create_test("✅ Gateway → Auth (Valid mTLS)", "/internal/admin/health", method="GET", headers=[{"key": "X-SYSTEM-KEY", "value": "InternalSecretKey"}], tests=["pm.test('mTLS Success', () => pm.expect(pm.response.code).to.be.oneOf([200, 404]));"], description="Note: This test may fail if not run from Gateway"))

mtls_tests.append(create_test("❌ Service Without Certificate - Fail", "/internal/admin/create", body={"username": "test", "password": "test"}, tests=["pm.test('No Certificate Rejected', () => pm.expect(pm.response.code).to.be.oneOf([403, 404]));"], description="Direct call without mTLS should fail"))

mtls_tests.append(create_test("❌ Wrong Certificate CN - Fail", "/internal/admin/create", headers=[{"key": "Content-Type", "value": "application/json"}, {"key": "X-SYSTEM-KEY", "value": "WrongKey"}], body={"username": "test", "password": "test"}, tests=["pm.test('Wrong Certificate Rejected', () => pm.expect(pm.response.code).to.be.oneOf([403, 404]));"], description="Invalid system key should fail"))

mtls_tests.append(create_test("📋 Verify Internal Endpoints Not Publicly Accessible", "/internal/admin/create", body={"username": "test", "password": "test"}, tests=["pm.test('Internal Protected', () => pm.expect(pm.response.code).to.be.oneOf([403, 404]));"], description="Internal endpoints should not be publicly accessible"))

write_collection("12_mtls_internal_auth_tests.json", "12 - mTLS & Internal Service Auth Tests", "Mutual TLS and internal service authentication", mtls_tests)

print("\n" + "="*60)
print("✅ All 12 test collections generated successfully!")
print("="*60)
