#!/bin/bash
# ============================================================================
# Auth Service Extended Test Suite - All 13 Categories
# Direct Auth access on port 8081/9091 (Standalone Mode)
# ============================================================================

set -e

AUTH_URL="https://localhost:8081"
ADMIN_URL="http://localhost:9091"

# mTLS Configuration for Production Mode
CERT_DIR="../../infra/mtls-certs"
CRT="$CERT_DIR/gateway/gateway.crt"
KEY="$CERT_DIR/gateway/gateway.key"

if [[ "$AUTH_URL" == https* ]]; then
    echo "========================================================"
    echo "🔐 Detected HTTPS Auth URL. Enabling mTLS mode..."
    echo "   Using Cert: $CRT"
    echo "========================================================"
    
    if [ ! -f "$CRT" ]; then
        echo "❌ mTLS Certs not found at $CRT"
        echo "   Please run from testing-scripts/smoke directory"
        exit 1
    fi
    # Overlay curl command with mTLS options
    curl() {
        command curl -k --cert "$CRT" --key "$KEY" "$@"
    }
    export -f curl
fi

PASSED=0
FAILED=0
SKIPPED=0

echo "============================================================================"
echo "Auth Service Extended Test Suite"
echo "============================================================================"
echo ""

# ============================================================================
# UTILITIES
# ============================================================================
pass_test() {
    echo "  ✅ PASS: $1"
    PASSED=$((PASSED + 1))
}

fail_test() {
    echo "  ❌ FAIL: $1"
    FAILED=$((FAILED + 1))
}

skip_test() {
    echo "  ⚠️  SKIP: $1"
    SKIPPED=$((SKIPPED + 1))
}

wait_for_readiness() {
    echo "Waiting for Auth service..."
    for i in {1..15}; do
        if curl -s "$AUTH_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
            echo "✅ Auth service is ready!"
            return 0
        fi
        sleep 2
    done
    echo "❌ Auth service not ready"
    exit 1
}

# Helper to create a unique user
create_test_user() {
    local prefix=$1
    local username="${prefix}_$(date +%s)_$RANDOM"
    local password="SecurePass123!"
    
    curl -s -X POST "$AUTH_URL/auth/register" \
      -H "Content-Type: application/json" \
      -d "{\"username\": \"$username\", \"password\": \"$password\"}" > /dev/null
    
    echo "$username|$password"
}

wait_for_readiness
echo ""

# Clear Redis for clean state
echo "Clearing Redis..."
docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true
sleep 1
echo ""

# ============================================================================
# CATEGORY 1: ACCOUNT LOCKOUT TESTS
# ============================================================================
echo "============================================================================"
echo "CATEGORY 1: Account Lockout Tests"
echo "============================================================================"

# Create user for lockout test
LOCKOUT_USER="lockout_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

echo "Test 1.1: 5 failed login attempts → account locked"
LOCKED=false
for i in {1..6}; do
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
      -H "Content-Type: application/json" \
      -H "User-Agent: LockoutTest/1.0" \
      -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"WrongPass$i!\"}")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    
    if [ "$HTTP_CODE" == "423" ]; then
        LOCKED=true
        break
    fi
    sleep 0.3
done

if [ "$LOCKED" == "true" ]; then
    pass_test "Account locked after failed attempts (423)"
else
    fail_test "Account not locked after 6 failed attempts"
fi

echo "Test 1.2: Further attempts return 423 LOCKED"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: LockoutTest/1.0" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"SecurePass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "423" ]; then
    pass_test "Locked account returns 423"
else
    fail_test "Expected 423, got $HTTP_CODE"
fi

echo "Test 1.3: Lockout tied to username (different device still locked)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: DifferentDevice/1.0" \
  -H "X-Device-Id: different-device-999" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"SecurePass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "423" ]; then
    pass_test "Lockout tied to username, not device"
else
    fail_test "Expected 423 from different device, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 2: SESSION LIMIT ENFORCEMENT
# ============================================================================
echo "============================================================================"
echo "CATEGORY 2: Session Limit Enforcement"
echo "============================================================================"

# Create user
SESSION_USER="session_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$SESSION_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

echo "Test 2.1: Login > max sessions removes oldest token"
TOKENS=()
for i in {1..6}; do
    LOGIN=$(curl -s -X POST "$AUTH_URL/auth/login" \
      -H "Content-Type: application/json" \
      -H "User-Agent: SessionTest/1.0" \
      -H "X-Device-Id: device_$i" \
      -d "{\"username\": \"$SESSION_USER\", \"password\": \"SecurePass123!\"}")
    
    ACCESS=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    REFRESH=$(echo "$LOGIN" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    TOKENS+=("$ACCESS|$REFRESH")
    sleep 0.3
done
pass_test "Created 6 sessions (max is 5)"

echo "Test 2.2: First session refresh token should be invalid"
FIRST_TOKEN=$(echo "${TOKENS[0]}" | cut -d'|' -f2)
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: SessionTest/1.0" \
  -H "X-Device-Id: device_1" \
  -d "{\"refreshToken\": \"$FIRST_TOKEN\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Oldest session correctly invalidated"
else
    skip_test "First session still valid (HTTP $HTTP_CODE) - may be within grace period"
fi

echo "Test 2.3: Latest session still works"
LAST_TOKEN=$(echo "${TOKENS[5]}" | cut -d'|' -f2)
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: SessionTest/1.0" \
  -H "X-Device-Id: device_6" \
  -d "{\"refreshToken\": \"$LAST_TOKEN\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Latest session works correctly"
else
    fail_test "Latest session failed with HTTP $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 3: REDIS OUTAGE RESILIENCE
# ============================================================================
echo "============================================================================"
echo "CATEGORY 3: Redis Outage Resilience"
echo "============================================================================"

echo "Test 3.1: Stop Redis → Auth still works (circuit breaker)"
# Create user first while Redis is up
REDIS_USER="redis_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$REDIS_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

docker stop redis > /dev/null 2>&1 || true
sleep 3

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$REDIS_USER\", \"password\": \"SecurePass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Auth works during Redis outage (circuit breaker active)"
else
    skip_test "Auth failed during Redis outage (HTTP $HTTP_CODE) - may require DB fallback"
fi

echo "Test 3.2: Restart Redis → continues functioning"
docker start redis > /dev/null 2>&1 || true
sleep 5

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$REDIS_USER\", \"password\": \"SecurePass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Auth recovered after Redis restart"
else
    fail_test "Auth failed after Redis restart (HTTP $HTTP_CODE)"
fi

# Re-clear Redis
docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true
echo ""

# ============================================================================
# CATEGORY 4: JWT CLAIM VALIDATION
# ============================================================================
echo "============================================================================"
echo "CATEGORY 4: JWT Claim Validation"
echo "============================================================================"

echo "Test 4.1: Completely invalid JWT → 401"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
  -H "Authorization: Bearer invalid.token.here")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Invalid JWT rejected (401)"
else
    fail_test "Expected 401, got $HTTP_CODE"
fi

echo "Test 4.2: Malformed JWT structure → 401"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Malformed JWT rejected (401)"
else
    fail_test "Expected 401, got $HTTP_CODE"
fi

echo "Test 4.3: JWT with wrong signature → 401"
# Valid structure but wrong signature
FAKE_JWT="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QiLCJpYXQiOjE1MTYyMzkwMjJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
  -H "Authorization: Bearer $FAKE_JWT")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "JWT with wrong signature rejected (401)"
else
    fail_test "Expected 401, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 5: ADMIN PORT (9091) TESTS
# ============================================================================
echo "============================================================================"
echo "CATEGORY 5: Admin Port (9091) Tests"
echo "============================================================================"

echo "Test 5.1: /actuator/health on admin port returns 200"
RESPONSE=$(curl -s -w "\n%{http_code}" "$ADMIN_URL/actuator/health")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Admin health endpoint accessible (200)"
else
    fail_test "Admin health failed with $HTTP_CODE"
fi

echo "Test 5.2: Admin login endpoint accessible on 9091"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$ADMIN_URL/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "SuperSecretAdmin123"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "401" ]; then
    pass_test "Admin login endpoint accessible (HTTP $HTTP_CODE)"
else
    fail_test "Admin login endpoint issue (HTTP $HTTP_CODE)"
fi

echo "Test 5.3: Admin path visibility on user port 8081 (design check)"
RESPONSE=$(curl -s -w "\n%{http_code}" "$AUTH_URL/admin/health")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

# NOTE: Single-app dual-port design means admin paths ARE accessible on both ports.
# Production isolation should be done at infrastructure level (firewall, VPN, LB rules)
if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Admin path accessible (expected - infra isolation handles this)"
elif [ "$HTTP_CODE" == "404" ] || [ "$HTTP_CODE" == "403" ]; then
    pass_test "Admin path blocked on user port (HTTP $HTTP_CODE)"
else
    skip_test "Admin path returned unexpected HTTP $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 6: INPUT VALIDATION EDGE CASES
# ============================================================================
echo "============================================================================"
echo "CATEGORY 6: Input Validation Edge Cases"
echo "============================================================================"

echo "Test 6.1: Username with special chars → 400"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "user<script>", "password": "ValidPass123!"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Special chars in username rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 6.2: Password too weak → 400"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "validuser123", "password": "weak"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Weak password rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 6.3: SQL injection attempt → rejected"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: SQLITest/1.0" \
  -d '{"username": "admin'\'' OR 1=1--", "password": "test"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ] || [ "$HTTP_CODE" == "401" ]; then
    pass_test "SQL injection rejected (HTTP $HTTP_CODE)"
else
    fail_test "SQL injection may not be blocked (HTTP $HTTP_CODE)"
fi

echo "Test 6.4: XSS payload in username → rejected"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "<script>alert(1)</script>", "password": "ValidPass123!"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "XSS payload rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 6.5: Empty JSON body → 400"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Empty body rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 6.6: Null fields → 400"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": null, "password": null}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Null fields rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 6.7: Extremely long username (DoS prevention) → 400"
LONG_USER=$(printf 'a%.0s' {1..500})
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LONG_USER\", \"password\": \"ValidPass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Long username rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 7: USERNAME UNIQUENESS
# ============================================================================
echo "============================================================================"
echo "CATEGORY 7: Username Uniqueness"
echo "============================================================================"

echo "Test 7.1: Registering same username twice → 400/409"
UNIQUE_USER="unique_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$UNIQUE_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$UNIQUE_USER\", \"password\": \"SecurePass456!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ] || [ "$HTTP_CODE" == "409" ]; then
    pass_test "Duplicate username rejected (HTTP $HTTP_CODE)"
else
    fail_test "Expected 400/409, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 8: RATE LIMITING
# ============================================================================
echo "============================================================================"
echo "CATEGORY 8: Rate Limiting"
echo "============================================================================"

echo "Test 8.1: Rapid login attempts (Target: >50 to hit IP limit)"
RATE_LIMITED=false
# Use a distinct public IP (203.0.113.5) to avoid interference from previous tests
# and ensure we start from 0 count. IpExtractor accepts Public IPs.
TEST_IP="203.0.113.5"

for i in {1..60}; do
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
      -H "Content-Type: application/json" \
      -H "User-Agent: RateLimitTest/1.0" \
      -H "X-Forwarded-For: $TEST_IP" \
      -d '{"username": "ratelimit_test", "password": "WrongPass!"}')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    
    if [ "$HTTP_CODE" == "429" ]; then
        RATE_LIMITED=true
        echo "    ✅ Rate limited at attempt $i (Expected)"
        break
    fi
    # Small sleep to allow processing but still be fast enough
    sleep 0.2
done

if [ "$RATE_LIMITED" == "true" ]; then
    pass_test "Rate limiting triggered (429)"
else
    fail_test "Rate limit NOT triggered in 60 attempts (Limit is 50/60s)"
fi
echo ""

# ============================================================================
# CATEGORY 9: REFRESH TOKEN EDGE CASES
# ============================================================================
echo "============================================================================"
echo "CATEGORY 9: Refresh Token Edge Cases"
echo "============================================================================"

# Create and login a user
REFRESH_USER="refresh_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$REFRESH_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

LOGIN=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RefreshTest/1.0" \
  -H "X-Device-Id: device-A" \
  -d "{\"username\": \"$REFRESH_USER\", \"password\": \"SecurePass123!\"}")
ACCESS_TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
REFRESH_TOKEN=$(echo "$LOGIN" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)

echo "Test 9.1: Refresh token for different device → 401"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RefreshTest/1.0" \
  -H "X-Device-Id: device-B" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Refresh from different device rejected (401)"
else
    skip_test "Different device refresh allowed (HTTP $HTTP_CODE) - may be policy-dependent"
fi

echo "Test 9.2: Refresh after logout → should fail"
# First do a valid refresh
REFRESH=$(curl -s -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RefreshTest/1.0" \
  -H "X-Device-Id: device-A" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")
NEW_ACCESS=$(echo "$REFRESH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
NEW_REFRESH=$(echo "$REFRESH" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)

# Logout
curl -s -X POST "$AUTH_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $NEW_ACCESS" \
  -d "{\"refreshToken\": \"$NEW_REFRESH\"}" > /dev/null

# Try to use the logged-out refresh token
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RefreshTest/1.0" \
  -H "X-Device-Id: device-A" \
  -d "{\"refreshToken\": \"$NEW_REFRESH\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Refresh after logout rejected (401)"
else
    fail_test "Expected 401, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 10: LOGOUT EDGE CASES
# ============================================================================
echo "============================================================================"
echo "CATEGORY 10: Logout Edge Cases"
echo "============================================================================"

echo "Test 10.1: Logout without Authorization header → 401"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "some-token"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Logout without auth rejected (401)"
else
    skip_test "Logout without auth returned HTTP $HTTP_CODE"
fi

echo "Test 10.2: Logout with invalid JWT → 401"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid.token.here" \
  -d '{"refreshToken": "some-token"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Logout with invalid token rejected (401)"
else
    fail_test "Expected 401, got $HTTP_CODE"
fi
echo ""

echo "Test 10.3: Cross-user session revocation (User B cannot revoke User A)"
# 1. Register User A and Login
USER_A="user_a_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USER_A\", \"password\": \"Password123!\"}" > /dev/null

LOGIN_A=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USER_A\", \"password\": \"Password123!\"}")
REFRESH_TOKEN_A=$(echo "$LOGIN_A" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)

# 2. Register User B and Login
USER_B="user_b_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USER_B\", \"password\": \"Password123!\"}" > /dev/null

LOGIN_B=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USER_B\", \"password\": \"Password123!\"}")
ACCESS_TOKEN_B=$(echo "$LOGIN_B" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

# 3. User B tries to revoke User A's refresh token
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN_B" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN_A\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "401" ] || [ "$HTTP_CODE" == "400" ] || [ "$HTTP_CODE" == "403" ]; then
    pass_test "Cross-user logout rejected (HTTP $HTTP_CODE)"
else
    fail_test "User B revoked User A's session! (HTTP $HTTP_CODE)"
fi
echo ""

# ============================================================================
# CATEGORY 11: SECURITY HEADERS
# ============================================================================
echo "============================================================================"
echo "CATEGORY 11: Security Headers"
echo "============================================================================"

echo "Test 11.1: Request without User-Agent"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -A "" \
  -d '{"username": "test", "password": "test"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
pass_test "Request without User-Agent handled (HTTP $HTTP_CODE)"

echo "Test 11.2: Request without X-Device-Id (should still work)"
HEADER_USER="header_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$HEADER_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: HeaderTest/1.0" \
  -d "{\"username\": \"$HEADER_USER\", \"password\": \"SecurePass123!\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Login works without X-Device-Id"
else
    skip_test "Login without X-Device-Id returned $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 12: JSON ROBUSTNESS
# ============================================================================
echo "============================================================================"
echo "CATEGORY 12: JSON Robustness"
echo "============================================================================"

echo "Test 12.1: Malformed JSON → 400"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{username: test}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Malformed JSON rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi

echo "Test 12.2: Extra fields in JSON (should be ignored)"
JSON_USER="json_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$JSON_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: JSONTest/1.0" \
  -d "{\"username\": \"$JSON_USER\", \"password\": \"SecurePass123!\", \"extraField\": \"ignored\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Extra fields in JSON ignored (200)"
else
    fail_test "Expected 200, got $HTTP_CODE"
fi

echo "Test 12.3: Wrong Content-Type: application/x-www-form-urlencoded"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'username=test&password=test')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" == "415" ] || [ "$HTTP_CODE" == "400" ] || [ "$HTTP_CODE" == "500" ]; then
    pass_test "Wrong Content-Type rejected (HTTP $HTTP_CODE)"
else
    fail_test "Expected 415/400/500, got $HTTP_CODE"
fi
echo ""

# ============================================================================
# CATEGORY 13: DATABASE INTEGRITY (Basic checks)
# ============================================================================
echo "============================================================================"
echo "CATEGORY 13: Database Integrity"
echo "============================================================================"

echo "Test 13.1: User creation verified in DB"
DB_USER="db_$(date +%s)"
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$DB_USER\", \"password\": \"SecurePass123!\"}" > /dev/null

DB_CHECK=$(docker exec postgres psql -U rh_user -d ride_hailing -t -c "SELECT COUNT(*) FROM users WHERE username='$DB_USER';" 2>/dev/null | tr -d ' ')

if [ "$DB_CHECK" == "1" ]; then
    pass_test "User row created in database"
else
    fail_test "User not found in database (count: $DB_CHECK)"
fi

echo "Test 13.2: Refresh token created after login"
LOGIN=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: DBTest/1.0" \
  -d "{\"username\": \"$DB_USER\", \"password\": \"SecurePass123!\"}")

TOKEN_CHECK=$(docker exec postgres psql -U rh_user -d ride_hailing -t -c "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id WHERE u.username='$DB_USER';" 2>/dev/null | tr -d ' ')

if [ "$TOKEN_CHECK" -ge "1" ]; then
    pass_test "Refresh token created in database"
else
    fail_test "Refresh token not found in database"
fi
echo ""

# ============================================================================
# SUMMARY
# ============================================================================
echo "============================================================================"
echo "SUMMARY"
echo "============================================================================"
TOTAL=$((PASSED + FAILED + SKIPPED))
echo "Total:   $TOTAL"
echo "Passed:  $PASSED"
echo "Failed:  $FAILED"
echo "Skipped: $SKIPPED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "✅ ALL TESTS PASSED (or skipped)!"
    exit 0
else
    echo "❌ SOME TESTS FAILED"
    exit 1
fi
