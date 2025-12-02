#!/usr/bin/env python3
"""
Refresh Token Test Script
Tests: Valid refresh, expired refresh, blacklisted JTI, token replay,
different IP/device, malformed/tampered tokens
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
        "name": "03 - Refresh Token Tests",
        "description": "Comprehensive refresh token rotation and security testing",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": [],
    "variable": [
        {"key": "base_url", "value": "http://localhost:8080"},
        {"key": "test_user", "value": f"refreshtest_{uuid.uuid4().hex[:8]}"},
        {"key": "test_password", "value": "SecurePass123!"},
        {"key": "access_token", "value": ""},
        {"key": "refresh_token", "value": ""},
        {"key": "old_refresh_token", "value": ""}
    ]
}

tests = []

# ============================================================================
# SETUP: Register and Login
# ============================================================================
tests.append(create_test(
    "📋 Setup - Register User",
    "/auth/register",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('User Created', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));"
    ],
    delay_ms=500
))

tests.append(create_test(
    "📋 Setup - Login User",
    "/auth/login",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('Login Success', () => pm.response.to.have.status(200));",
        "const resp = pm.response.json();",
        "pm.environment.set('access_token', resp.accessToken);",
        "pm.environment.set('refresh_token', resp.refreshToken);"
    ],
    delay_ms=1000
))

# ============================================================================
# VALID REFRESH
# ============================================================================
tests.append(create_test(
    "✅ Valid Refresh Token - First Rotation",
    "/auth/refresh",
    body={"refreshToken": "{{refresh_token}}"},
    tests=[
        "pm.test('Refresh Success', () => pm.response.to.have.status(200));",
        "const resp = pm.response.json();",
        "pm.test('New accessToken', () => pm.expect(resp.accessToken).to.exist);",
        "pm.test('New refreshToken', () => pm.expect(resp.refreshToken).to.exist);",
        "pm.test('Tokens Changed', () => {",
        "    pm.expect(resp.accessToken).to.not.eql(pm.environment.get('access_token'));",
        "    pm.expect(resp.refreshToken).to.not.eql(pm.environment.get('refresh_token'));",
        "});",
        "",
        "// Store old refresh token for replay test",
        "pm.environment.set('old_refresh_token', pm.environment.get('refresh_token'));",
        "pm.environment.set('access_token', resp.accessToken);",
        "pm.environment.set('refresh_token', resp.refreshToken);"
    ],
    description="First token rotation - verify token changes",
    delay_ms=500
))

# ============================================================================
# TOKEN REPLAY (Used Twice)
# ============================================================================
tests.append(create_test(
    "❌ Replay Attack - Reuse Old Refresh Token",
    "/auth/refresh",
    body={"refreshToken": "{{old_refresh_token}}"},
    tests=[
        "pm.test('Replay Blocked', () => pm.response.to.have.status(401));",
        "pm.test('Error Message', () => pm.expect(pm.response.json().error).to.exist);"
    ],
    description="Single-use refresh token validation"
))

# ============================================================================
# VALID REFRESH - Second Rotation
# ============================================================================
tests.append(create_test(
    "✅ Valid Refresh Token - Second Rotation",
    "/auth/refresh",
    body={"refreshToken": "{{refresh_token}}"},
    tests=[
        "pm.test('2nd Refresh Success', () => pm.response.to.have.status(200));",
        "const resp = pm.response.json();",
        "pm.environment.set('access_token', resp.accessToken);",
        "pm.environment.set('refresh_token', resp.refreshToken);"
    ],
    description="Verify token rotation works multiple times",
    delay_ms=500
))

# ============================================================================
# EXPIRED REFRESH TOKEN
# ============================================================================
tests.append(create_test(
    "❌ Expired Refresh Token",
    "/auth/refresh",
    body={"refreshToken": str(uuid.uuid4())},
    tests=[
        "pm.test('Expired Token Rejected', () => pm.response.to.have.status(401));"
    ],
    description="Simulate expired/invalid refresh token UUID"
))

# ============================================================================
# BLACKLISTED JTI (via logout)
# ============================================================================
tests.append(create_test(
    "📋 Setup - Logout to Blacklist Tokens",
    "/auth/logout",
    headers=[
        {"key": "Content-Type", "value": "application/json"},
        {"key": "Authorization", "value": "Bearer {{access_token}}"}
    ],
    body={"refreshToken": "{{refresh_token}}"},
    tests=[
        "pm.test('Logout Success', () => pm.response.to.have.status(200));",
        "pm.environment.set('blacklisted_refresh', pm.environment.get('refresh_token'));"
    ]
))

tests.append(create_test(
    "❌ Blacklisted Refresh Token",
    "/auth/refresh",
    body={"refreshToken": "{{blacklisted_refresh}}"},
    tests=[
        "pm.test('Blacklisted Token Rejected', () => pm.response.to.have.status(401));"
    ],
    description="Refresh token invalidated after logout"
))

# ============================================================================
# DIFFERENT IP / DEVICE
# ============================================================================
tests.append(create_test(
    "📋 Setup - Login Again for IP/Device Tests",
    "/auth/login",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('Re-login Success', () => pm.response.to.have.status(200));",
        "const resp = pm.response.json();",
        "pm.environment.set('access_token', resp.accessToken);",
        "pm.environment.set('refresh_token', resp.refreshToken);"
    ],
    delay_ms=1000
))

tests.append(create_test(
    "🔍 Refresh with Different Device-ID",
    "/auth/refresh",
    headers=[
        {"key": "Content-Type", "value": "application/json"},
        {"key": "X-Device-ID", "value": "different-device-uuid"}
    ],
    body={"refreshToken": "{{refresh_token}}"},
    tests=[
        "pm.test('Refresh Response', () => pm.expect(pm.response.code).to.be.oneOf([200, 401]));"
    ],
    description="Test if device mismatch affects refresh"
))

tests.append(create_test(
    "🔍 Refresh with Different IP",
    "/auth/refresh",
    headers=[
        {"key": "Content-Type", "value": "application/json"},
        {"key": "X-Forwarded-For", "value": "192.168.100.200"}
    ],
    body={"refreshToken": "{{refresh_token}}"},
    tests=[
        "pm.test('Refresh Response', () => pm.expect(pm.response.code).to.be.oneOf([200, 401]));"
    ],
    description="Test if IP change affects refresh"
))

# ============================================================================
# MALFORMED REFRESH TOKENS
# ============================================================================
malformed_tokens = [
    ("Empty Token", ""),
    ("Invalid UUID", "not-a-uuid"),
    ("SQL Injection", "'; DROP TABLE tokens; --"),
    ("XSS Attempt", "<script>alert(1)</script>"),
    ("Null Byte", "token\x00malicious"),
    ("Very Long Token", "a" * 1000),
    ("Special Characters", "!@#$%^&*()"),
    ("Null Value", None)
]

for name, token in malformed_tokens:
    if token is None:
        body = {"refreshToken": token}
    else:
        body = {"refreshToken": token}
    
    tests.append(create_test(
        f"❌ Malformed Token - {name}",
        "/auth/refresh",
        body=body,
        tests=[
            "pm.test('Malformed Token Rejected', () => pm.response.to.have.status(401));"
        ]
    ))

# ============================================================================
# TAMPERED REFRESH TOKENS
# ============================================================================
tests.append(create_test(
    "❌ Tampered Token - Modified UUID",
    "/auth/refresh",
    body={"refreshToken": "00000000-0000-0000-0000-000000000000"},
    tests=[
        "pm.test('Tampered Token Rejected', () => pm.response.to.have.status(401));"
    ]
))

tests.append(create_test(
    "❌ Tampered Token - Partial Modification",
    "/auth/refresh",
    body={},
    tests=[
        "// Get current refresh token and modify it",
        "var token = pm.environment.get('refresh_token');",
        "if (token) {",
        "    var parts = token.split('-');",
        "    parts[0] = '99999999';",
        "    var tamperedToken = parts.join('-');",
        "    pm.request.body.raw = JSON.stringify({refreshToken: tamperedToken});",
        "}",
        "pm.test('Tampered Token Rejected', () => pm.response.to.have.status(401));"
    ]
))

# ============================================================================
# MISSING REFRESH TOKEN
# ============================================================================
tests.append(create_test(
    "❌ Missing Refresh Token",
    "/auth/refresh",
    body={},
    tests=[
        "pm.test('Missing Token Rejected', () => pm.response.to.have.status(401));"
    ]
))

# ============================================================================
# MALFORMED JSON BODY
# ============================================================================
malformed_json = [
    ("Array Instead of Object", "[]"),
    ("String Instead of Object", '"string"'),
    ("Incomplete JSON", '{"refreshToken": "'),
    ("Invalid JSON Syntax", '{"refreshToken": }')
]

for name, json_str in malformed_json:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/refresh",
        body=json_str,
        tests=[
            "pm.test('Malformed JSON Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 401, 500]));"
        ]
    ))

collection["item"] = tests

# Write to file
with open("03_refresh_token_tests.json", "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=2)

print(f"✅ Generated {len(tests)} refresh token tests → 03_refresh_token_tests.json")
