#!/usr/bin/env python3
"""
Login Test Script
Tests: Valid login, wrong password/username, locked account, rate limit,
invalid device-id, malformed JSON, SQLi/XSS payloads
"""
import json
import uuid

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

collection = {
    "info": {
        "_postman_id": str(uuid.uuid4()),
        "name": "02 - Login Tests",
        "description": "Comprehensive login endpoint testing including security and rate limiting",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": [],
    "variable": [
        {"key": "base_url", "value": "http://localhost:8080"},
        {"key": "test_user", "value": f"logintest_{uuid.uuid4().hex[:8]}"},
        {"key": "test_password", "value": "SecurePass123!"},
        {"key": "access_token", "value": ""},
        {"key": "refresh_token", "value": ""}
    ]
}

tests = []

# ============================================================================
# SETUP: Register test user
# ============================================================================
tests.append(create_test(
    "📋 Setup - Register Test User",
    "/auth/register",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('User Created', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));"
    ],
    delay_ms=500
))

# ============================================================================
# VALID LOGIN
# ============================================================================
tests.append(create_test(
    "✅ Valid Login",
    "/auth/login",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('Login Success', () => pm.response.to.have.status(200));",
        "const resp = pm.response.json();",
        "pm.test('Has accessToken', () => pm.expect(resp.accessToken).to.exist);",
        "pm.test('Has refreshToken', () => pm.expect(resp.refreshToken).to.exist);",
        "pm.environment.set('access_token', resp.accessToken);",
        "pm.environment.set('refresh_token', resp.refreshToken);"
    ],
    description="Successful login with valid credentials",
    delay_ms=1000
))

# ============================================================================
# WRONG PASSWORD
# ============================================================================
tests.append(create_test(
    "❌ Wrong Password",
    "/auth/login",
    body={"username": "{{test_user}}", "password": "WrongPassword123"},
    tests=[
        "pm.test('Login Failed', () => pm.response.to.have.status(401));",
        "pm.test('Error Message', () => pm.expect(pm.response.json().error).to.exist);"
    ],
    description="Login attempt with incorrect password",
    delay_ms=500
))

# ============================================================================
# WRONG USERNAME
# ============================================================================
tests.append(create_test(
    "❌ Wrong Username",
    "/auth/login",
    body={"username": "nonexistentuser", "password": "{{test_password}}"},
    tests=[
        "pm.test('Login Failed', () => pm.response.to.have.status(401));",
        "pm.test('Error Message', () => pm.expect(pm.response.json().error).to.exist);"
    ],
    description="Login attempt with non-existent username",
    delay_ms=500
))

# ============================================================================
# LOCKED ACCOUNT (5 failed attempts)
# ============================================================================
tests.append(create_test(
    "📋 Setup - Create Lockout User",
    "/auth/register",
    body={"username": "lockoutuser_{{$guid}}", "password": "SecurePass123!"},
    tests=[
        "pm.test('User Created', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));",
        "var reqBody = JSON.parse(pm.request.body.raw);",
        "pm.environment.set('lockout_user', reqBody.username);"
    ]
))

for i in range(1, 6):
    tests.append(create_test(
        f"❌ Failed Login Attempt #{i}",
        "/auth/login",
        body={"username": "{{lockout_user}}", "password": "WrongPassword"},
        tests=[
            f"pm.test('Attempt #{i} Failed', () => pm.expect(pm.response.code).to.be.oneOf([401, 423]));"
        ],
        delay_ms=500
    ))

tests.append(create_test(
    "🔒 Account Locked",
    "/auth/login",
    body={"username": "{{lockout_user}}", "password": "SecurePass123!"},
    tests=[
        "pm.test('Account Locked', () => pm.response.to.have.status(423));",
        "pm.test('Locked Message', () => pm.expect(pm.response.json().error).to.include('locked'));"
    ],
    description="Verify account locks after 5 failed attempts",
    delay_ms=1000
))

# ============================================================================
# RATE LIMIT (Too Many Attempts)
# ============================================================================
tests.append(create_test(
    "📋 Rate Limit - Create Test User",
    "/auth/register",
    body={"username": "ratelimituser_{{$guid}}", "password": "SecurePass123!"},
    tests=[
        "pm.test('User Created', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));",
        "var reqBody = JSON.parse(pm.request.body.raw);",
        "pm.environment.set('ratelimit_user', reqBody.username);"
    ]
))

for i in range(1, 8):
    tests.append(create_test(
        f"⏱️ Rapid Login Attempt #{i}",
        "/auth/login",
        body={"username": "{{ratelimit_user}}", "password": "SecurePass123!"},
        tests=[
            f"pm.test('Attempt #{i}', () => pm.expect(pm.response.code).to.be.oneOf([200, 429]));"
        ],
        delay_ms=100
    ))

# ============================================================================
# INVALID DEVICE-ID
# ============================================================================
invalid_device_ids = [
    ("Missing Device-ID", None),
    ("Empty Device-ID", ""),
    ("Invalid Format Device-ID", "not-a-uuid"),
    ("SQL Injection Device-ID", "'; DROP TABLE--")
]

for name, device_id in invalid_device_ids:
    headers = [{"key": "Content-Type", "value": "application/json"}]
    if device_id is not None:
        headers.append({"key": "X-Device-ID", "value": device_id})
    
    tests.append(create_test(
        f"❌ {name}",
        "/auth/login",
        headers=headers,
        body={"username": "{{test_user}}", "password": "{{test_password}}"},
        tests=[
            "pm.test('Login Response', () => pm.expect(pm.response.code).to.be.oneOf([200, 400]));"
        ],
        description=f"Login with {name.lower()}"
    ))

# ============================================================================
# MALFORMED JSON
# ============================================================================
malformed_json = [
    ("Incomplete JSON", '{"username": "test"'),
    ("Invalid JSON Syntax", '{"username": "test",}'),
    ("Array Instead of Object", '[]'),
    ("String Instead of Object", '"just a string"'),
    ("Number Instead of Object", '12345')
]

for name, json_str in malformed_json:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/login",
        body=json_str,
        tests=[
            "pm.test('Malformed JSON Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 500]));"
        ]
    ))

# ============================================================================
# SQL INJECTION PAYLOADS
# ============================================================================
sql_payloads = [
    "admin' OR '1'='1",
    "'; DROP TABLE users; --",
    "admin'--",
    "1' UNION SELECT NULL--",
    "' OR 1=1--",
    "admin' AND '1'='1",
    "admin' /*"
]

for i, payload in enumerate(sql_payloads):
    tests.append(create_test(
        f"🛡️ SQLi Login #{i+1}",
        "/auth/login",
        body={"username": payload, "password": "anything"},
        tests=[
            "pm.test('SQL Injection Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 401, 423, 429]));"
        ],
        description=f"SQL injection via username: {payload[:30]}...",
        delay_ms=300
    ))

# ============================================================================
# XSS PAYLOADS
# ============================================================================
xss_payloads = [
    "<script>alert('xss')</script>",
    "<img src=x onerror=alert(1)>",
    "javascript:alert(1)",
    "<svg onload=alert(1)>",
    "'\"><script>alert('xss')</script>"
]

for i, payload in enumerate(xss_payloads):
    tests.append(create_test(
        f"🛡️ XSS Login #{i+1}",
        "/auth/login",
        body={"username": payload, "password": "anything"},
        tests=[
            "pm.test('XSS Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 401, 423, 429]));"
        ],
        description=f"XSS via username: {payload[:30]}...",
        delay_ms=300
    ))

# ============================================================================
# MISSING FIELDS
# ============================================================================
missing_fields = [
    ("Missing Username", {"password": "password"}),
    ("Missing Password", {"username": "username"}),
    ("Empty Username", {"username": "", "password": "password"}),
    ("Empty Password", {"username": "username", "password": ""}),
    ("Null Username", {"username": None, "password": "password"}),
    ("Null Password", {"username": "username", "password": None}),
    ("Empty Body", {})
]

for name, body in missing_fields:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/login",
        body=body,
        tests=[
            "pm.test('Missing Field Rejected', () => pm.response.to.have.status(400));"
        ]
    ))

collection["item"] = tests

# Write to file
with open("02_login_tests.json", "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=2)

print(f"✅ Generated {len(tests)} login tests → 02_login_tests.json")
