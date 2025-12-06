#!/bin/bash
# ============================================================================
# Auth Service Standalone Smoke Tests
# Direct Auth access on port 8081 (NO Gateway)
# ============================================================================

set -e

AUTH_URL="https://localhost:8081"
ADMIN_URL="http://localhost:9091"

# mTLS Configuration for Production Mode
CERT_DIR="../../infra/mtls-certs"
CRT="$CERT_DIR/gateway/gateway.crt"
KEY="$CERT_DIR/gateway/gateway.key"

if [[ "$AUTH_URL" == https* ]]; then
    if [ ! -f "$CRT" ]; then
        echo "❌ mTLS Certs not found at $CRT"
        echo "   Please run from testing-scripts/smoke directory"
        exit 1
    fi
     echo "========================================================"
    echo "🔐 Detected HTTPS Auth URL. Enabling mTLS mode..."
    echo "   Using Cert: $CRT"
    echo "========================================================"
   
    # Overlay curl command with mTLS options
    curl() {
        command curl -k --cert "$CRT" --key "$KEY" "$@"
    }
    export -f curl
fi

echo "=========================================="
echo "Auth Service Standalone Smoke Tests"
echo "=========================================="
echo ""

# ============================================================================
# WAIT FOR SERVICE READINESS
# ============================================================================
wait_for_readiness() {
    echo "Waiting for Auth service to be ready..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "$AUTH_URL/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; then
            echo "✅ Auth service is ready!"
            return 0
        fi
        
        # Fallback to regular health check
        if curl -s "$AUTH_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
            echo "✅ Auth service is ready!"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo "  Waiting for readiness... ($attempt/$max_attempts)"
        sleep 2
    done
    
    echo "❌ FAIL: Auth service not ready after $max_attempts attempts"
    exit 1
}

wait_for_readiness
echo ""

# ============================================================================
# TEST VARIABLES
# ============================================================================
USERNAME="standalone_test_$(date +%s)"
PASSWORD="SecurePass123!"
PASSED=0
FAILED=0

# ============================================================================
# TEST FUNCTIONS
# ============================================================================
pass_test() {
    echo "✅ PASS: $1"
    PASSED=$((PASSED + 1))
}

fail_test() {
    echo "❌ FAIL: $1"
    FAILED=$((FAILED + 1))
}

# ============================================================================
# TEST 1: JWKS Endpoint Accessibility
# ============================================================================
echo "Test 1: JWKS Endpoint"
JWKS=$(curl -s "$AUTH_URL/auth/keys/jwks.json")
if echo "$JWKS" | grep -q '"keys"'; then
    pass_test "JWKS endpoint accessible"
else
    fail_test "JWKS endpoint not accessible: $JWKS"
fi

# ============================================================================
# TEST 2: User Registration
# ============================================================================
echo ""
echo "Test 2: User Registration"
REGISTER=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

HTTP_CODE=$(echo "$REGISTER" | tail -1)
BODY=$(echo "$REGISTER" | head -n -1)

if [ "$HTTP_CODE" == "200" ]; then
    pass_test "User registered successfully"
else
    fail_test "Registration failed (HTTP $HTTP_CODE): $BODY"
fi

# ============================================================================
# TEST 3: Login with Valid Credentials
# ============================================================================
echo ""
echo "Test 3: Login with Valid Credentials"
LOGIN=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: StandaloneTest/1.0" \
  -H "X-Device-Id: test-device-001" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

HTTP_CODE=$(echo "$LOGIN" | tail -1)
BODY=$(echo "$LOGIN" | head -n -1)

if [ "$HTTP_CODE" == "200" ] && echo "$BODY" | grep -q '"accessToken"'; then
    ACCESS_TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    REFRESH_TOKEN=$(echo "$BODY" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    pass_test "Login successful"
else
    fail_test "Login failed (HTTP $HTTP_CODE): $BODY"
fi

# ============================================================================
# TEST 4: Login with Invalid Password
# ============================================================================
echo ""
echo "Test 4: Login with Invalid Password"
INVALID_LOGIN=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: StandaloneTest/1.0" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"WrongPassword123!\"}")

HTTP_CODE=$(echo "$INVALID_LOGIN" | tail -1)
BODY=$(echo "$INVALID_LOGIN" | head -n -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Invalid password correctly rejected (401)"
else
    fail_test "Expected 401, got HTTP $HTTP_CODE"
fi

# ============================================================================
# TEST 5: Login with Invalid Username
# ============================================================================
echo ""
echo "Test 5: Login with Invalid Username"
INVALID_USER=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: StandaloneTest/1.0" \
  -d "{\"username\": \"nonexistent_user_xyz\", \"password\": \"AnyPassword123!\"}")

HTTP_CODE=$(echo "$INVALID_USER" | tail -1)
BODY=$(echo "$INVALID_USER" | head -n -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Invalid username correctly rejected (401)"
else
    fail_test "Expected 401, got HTTP $HTTP_CODE"
fi

# ============================================================================
# TEST 6: Token Refresh
# ============================================================================
echo ""
echo "Test 6: Token Refresh"
if [ -n "$REFRESH_TOKEN" ]; then
    REFRESH=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
      -H "Content-Type: application/json" \
      -H "User-Agent: StandaloneTest/1.0" \
      -H "X-Device-Id: test-device-001" \
      -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

    HTTP_CODE=$(echo "$REFRESH" | tail -1)
    BODY=$(echo "$REFRESH" | head -n -1)

    if [ "$HTTP_CODE" == "200" ] && echo "$BODY" | grep -q '"accessToken"'; then
        NEW_ACCESS_TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
        NEW_REFRESH_TOKEN=$(echo "$BODY" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
        pass_test "Token refresh successful"
    else
        fail_test "Token refresh failed (HTTP $HTTP_CODE): $BODY"
    fi
else
    fail_test "No refresh token available (login failed)"
fi

# ============================================================================
# TEST 7: Replay Token Protection
# ============================================================================
echo ""
echo "Test 7: Replay Token Protection (reuse old refresh token)"
if [ -n "$REFRESH_TOKEN" ]; then
    # Try to use the old refresh token again (should fail)
    REPLAY=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
      -H "Content-Type: application/json" \
      -H "User-Agent: StandaloneTest/1.0" \
      -H "X-Device-Id: test-device-001" \
      -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

    HTTP_CODE=$(echo "$REPLAY" | tail -1)

    if [ "$HTTP_CODE" == "401" ]; then
        pass_test "Replay attack prevented (old token rejected)"
    else
        fail_test "Expected 401 for replay attack, got HTTP $HTTP_CODE"
    fi
else
    fail_test "No refresh token available for replay test"
fi

# ============================================================================
# TEST 8: Logout
# ============================================================================
echo ""
echo "Test 8: Logout"
if [ -n "$NEW_REFRESH_TOKEN" ]; then
    LOGOUT=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
      -d "{\"refreshToken\": \"$NEW_REFRESH_TOKEN\"}")

    HTTP_CODE=$(echo "$LOGOUT" | tail -1)

    if [ "$HTTP_CODE" == "200" ]; then
        pass_test "Logout successful"
    else
        fail_test "Logout failed (HTTP $HTTP_CODE)"
    fi
else
    fail_test "No refresh token available for logout"
fi

# ============================================================================
# TEST 9: Missing Fields (username)
# ============================================================================
echo ""
echo "Test 9: Missing Required Fields (username)"
MISSING=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"password\": \"SomePassword123!\"}")

HTTP_CODE=$(echo "$MISSING" | tail -1)

if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Missing username correctly rejected (400)"
else
    fail_test "Expected 400, got HTTP $HTTP_CODE"
fi

# ============================================================================
# TEST 10: Wrong Content-Type
# ============================================================================
echo ""
echo "Test 10: Wrong Content-Type"
WRONG_CT=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: text/plain" \
  -d "username=test&password=test")

HTTP_CODE=$(echo "$WRONG_CT" | tail -1)

if [ "$HTTP_CODE" == "415" ] || [ "$HTTP_CODE" == "400" ] || [ "$HTTP_CODE" == "500" ]; then
    pass_test "Wrong content-type correctly rejected (HTTP $HTTP_CODE)"
else
    fail_test "Expected 415, 400, or 500, got HTTP $HTTP_CODE"
fi

# ============================================================================
# TEST 11: Token Tampering
# ============================================================================
echo ""
echo "Test 11: Token Tampering (modified JWT)"
TAMPERED_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0YW1wZXJlZCJ9.invalid"
TAMPERED=$(curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
  -H "Authorization: Bearer $TAMPERED_TOKEN")

HTTP_CODE=$(echo "$TAMPERED" | tail -1)

if [ "$HTTP_CODE" == "401" ]; then
    pass_test "Tampered token correctly rejected (401)"
else
    fail_test "Expected 401, got HTTP $HTTP_CODE"
fi

# ============================================================================
# SUMMARY
# ============================================================================
echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "✅ ALL TESTS PASSED!"
    exit 0
else
    echo "❌ SOME TESTS FAILED"
    exit 1
fi
