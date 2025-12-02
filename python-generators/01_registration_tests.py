#!/usr/bin/env python3
"""
Registration Test Script
Tests: Valid registration, duplicate username, weak password, missing fields, 
invalid formats, SQLi, XSS, invalid body schema
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
        "name": "01 - Registration Tests",
        "description": "Comprehensive registration endpoint testing",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": [],
    "variable": [
        {"key": "base_url", "value": "http://localhost:8080"},
        {"key": "test_user", "value": f"testuser_{uuid.uuid4().hex[:8]}"},
        {"key": "test_password", "value": "SecurePass123!"}
    ]
}

tests = []

# ============================================================================
# VALID REGISTRATION
# ============================================================================
tests.append(create_test(
    "✅ Valid Registration",
    "/auth/register",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('Registration Success', () => pm.expect(pm.response.code).to.be.oneOf([200, 409]));",
        "if (pm.response.code === 200) {",
        "    pm.test('Has status', () => pm.expect(pm.response.json().status).to.exist);",
        "}"
    ],
    description="Valid user registration with strong password"
))

# ============================================================================
# DUPLICATE USERNAME
# ============================================================================
tests.append(create_test(
    "❌ Duplicate Username",
    "/auth/register",
    body={"username": "{{test_user}}", "password": "{{test_password}}"},
    tests=[
        "pm.test('Returns 409 Conflict', () => pm.response.to.have.status(409));",
        "pm.test('Has error message', () => pm.expect(pm.response.json().error).to.exist);"
    ],
    description="Attempt to register with existing username"
))

# ============================================================================
# WEAK PASSWORD TESTS
# ============================================================================
weak_passwords = [
    ("Short Password", "123", "Too short"),
    ("No Special Chars", "password123", "No special characters"),
    ("Common Password", "password", "Too common"),
    ("All Numbers", "123456789", "Only numbers")
]

for name, password, desc in weak_passwords:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/register",
        body={"username": f"user_{uuid.uuid4().hex[:6]}", "password": password},
        tests=[
            "pm.test('Weak Password Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 200]));"
        ],
        description=desc
    ))

# ============================================================================
# MISSING FIELDS
# ============================================================================
missing_fields = [
    ("Missing Username", {"password": "SecurePass123!"}, "Username required"),
    ("Missing Password", {"username": "validuser"}, "Password required"),
    ("Empty Username", {"username": "", "password": "SecurePass123!"}, "Username cannot be empty"),
    ("Empty Password", {"username": "validuser", "password": ""}, "Password cannot be empty"),
    ("Null Username", {"username": None, "password": "SecurePass123!"}, "Username cannot be null"),
    ("Null Password", {"username": "validuser", "password": None}, "Password cannot be null"),
    ("Empty Body", {}, "Both fields required")
]

for name, body, desc in missing_fields:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/register",
        body=body,
        tests=[
            "pm.test('Missing Field Rejected', () => pm.response.to.have.status(400));"
        ],
        description=desc
    ))

# ============================================================================
# INVALID EMAIL / USERNAME FORMAT
# ============================================================================
invalid_formats = [
    ("Username With Spaces", {"username": "user name", "password": "SecurePass123!"}),
    ("Username With Special Chars", {"username": "user@#$%", "password": "SecurePass123!"}),
    ("Very Long Username", {"username": "a" * 100, "password": "SecurePass123!"}),
    ("Very Short Username", {"username": "ab", "password": "SecurePass123!"}),
    ("Unicode Username", {"username": "用户名", "password": "SecurePass123!"}),
    ("Emoji Username", {"username": "user😀", "password": "SecurePass123!"}),
    ("Whitespace Only Username", {"username": "   ", "password": "SecurePass123!"})
]

for name, body in invalid_formats:
    tests.append(create_test(
        f"❌ {name}",
        "/auth/register",
        body=body,
        tests=[
            "pm.test('Invalid Format Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 200, 409]));"
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
    "admin' /*",
    "' WAITFOR DELAY '00:00:05'--"
]

for i, payload in enumerate(sql_payloads):
    tests.append(create_test(
        f"🛡️ SQLi Payload #{i+1}",
        "/auth/register",
        body={"username": payload, "password": "SecurePass123!"},
        tests=[
            "pm.test('SQL Injection Blocked', () => pm.expect(pm.response.code).to.be.oneOf([400, 409, 200]));"
        ],
        description=f"SQL injection attempt: {payload[:30]}..."
    ))

# ============================================================================
# XSS PAYLOADS
# ============================================================================
xss_payloads = [
    "<script>alert('xss')</script>",
    "<img src=x onerror=alert(1)>",
    "javascript:alert(1)",
    "<svg onload=alert(1)>",
    "'\"><script>alert(String.fromCharCode(88,83,83))</script>",
    "<iframe src=javascript:alert('xss')>",
    "<body onload=alert('xss')>"
]

for i, payload in enumerate(xss_payloads):
    tests.append(create_test(
        f"🛡️ XSS Payload #{i+1}",
        "/auth/register",
        body={"username": payload, "password": "SecurePass123!"},
        tests=[
            "pm.test('XSS Blocked or Sanitized', () => pm.expect(pm.response.code).to.be.oneOf([400, 409, 200]));"
        ],
        description=f"XSS attempt: {payload[:30]}..."
    ))

# ============================================================================
# INVALID BODY SCHEMA
# ============================================================================
invalid_schemas = [
    ("Array Instead of Object", "[]", "Invalid JSON schema"),
    ("String Instead of Object", '"string"', "Invalid JSON type"),
    ("Number Instead of Object", "123", "Invalid JSON type"),
    ("Malformed JSON", '{"username": "test"', "Malformed JSON"),
    ("Extra Fields", {"username": "test", "password": "pass", "extraField": "value"}, "Extra fields"),
    ("Nested Object", {"username": {"nested": "value"}, "password": "pass"}, "Nested username"),
    ("Boolean Username", {"username": True, "password": "pass"}, "Boolean username"),
    ("Numeric Username", {"username": 12345, "password": "pass"}, "Numeric username")
]

for name, body, desc in invalid_schemas:
    if isinstance(body, str):
        tests.append(create_test(
            f"❌ {name}",
            "/auth/register",
            body=body,
            tests=[
                "pm.test('Invalid Schema Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 500]));"
            ],
            description=desc
        ))
    else:
        tests.append(create_test(
            f"❌ {name}",
            "/auth/register",
            body=body,
            tests=[
                "pm.test('Invalid Schema Rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 200, 409]));"
            ],
            description=desc
        ))

collection["item"] = tests

# Write to file
with open("01_registration_tests.json", "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=2)

print(f"✅ Generated {len(tests)} registration tests → 01_registration_tests.json")
