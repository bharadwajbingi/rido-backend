#!/bin/bash
# Comprehensive API Test Script for Rido Microservices
# Tests all endpoints via curl, then outputs results

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Base URLs
GATEWAY_URL="http://localhost:8080"
ADMIN_URL="http://localhost:9091"

# Variables
TEST_USERNAME="user_$(date +%s)"
TEST_PASSWORD="TestPass123!"
ACCESS_TOKEN=""
REFRESH_TOKEN=""
ADMIN_TOKEN=""
USER_ID=""

# Counters
PASSED=0
FAILED=0

pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((PASSED++))
}

fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    echo "  Response: $2"
    ((FAILED++))
}

section() {
    echo ""
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ================================================================
# 0. ADMIN SETUP
# ================================================================
section "0. ADMIN SETUP"

# Admin Health
RESP=$(curl -s -w "\n%{http_code}" "$ADMIN_URL/admin/health")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    pass "Admin Health Check (200)"
else
    fail "Admin Health Check" "$BODY"
fi

# Admin Login
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ADMIN_URL/admin/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"adminpass"}')
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    ADMIN_TOKEN=$(echo "$BODY" | jq -r '.accessToken // empty')
    if [ -n "$ADMIN_TOKEN" ]; then
        pass "Admin Login (200 + token)"
    else
        fail "Admin Login - no token" "$BODY"
    fi
else
    fail "Admin Login" "$BODY"
fi

# ================================================================
# 1. REGISTRATION TESTS
# ================================================================
section "1. REGISTRATION TESTS"

# [P] Valid Registration
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Valid Registration"
else
    fail "[P] Valid Registration" "$BODY"
fi

# [N] Missing Username
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"password":"TestPass123!"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "400" ]; then
    pass "[N] Missing Username (400)"
else
    fail "[N] Missing Username expected 400" "got $CODE"
fi

# [N] Missing Password
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"testuser"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "400" ]; then
    pass "[N] Missing Password (400)"
else
    fail "[N] Missing Password expected 400" "got $CODE"
fi

# [N] Username Too Short
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"ab","password":"TestPass123!"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "400" ]; then
    pass "[N] Username Too Short (400)"
else
    fail "[N] Username Too Short expected 400" "got $CODE"
fi

# [N] Password Too Short
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"validuser","password":"12345"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "400" ]; then
    pass "[N] Password Too Short (400)"
else
    fail "[N] Password Too Short expected 400" "got $CODE"
fi

# [N] Duplicate Username
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "409" ]; then
    pass "[N] Duplicate Username (409)"
else
    fail "[N] Duplicate Username expected 409" "got $CODE"
fi

# ================================================================
# 2. LOGIN TESTS
# ================================================================
section "2. LOGIN TESTS"

# [P] Valid Login
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Device-Id: test-device-001" \
    -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    ACCESS_TOKEN=$(echo "$BODY" | jq -r '.accessToken // empty')
    REFRESH_TOKEN=$(echo "$BODY" | jq -r '.refreshToken // empty')
    if [ -n "$ACCESS_TOKEN" ] && [ -n "$REFRESH_TOKEN" ]; then
        pass "[P] Valid Login (200 + tokens)"
    else
        fail "[P] Valid Login - missing tokens" "$BODY"
    fi
else
    fail "[P] Valid Login" "$BODY"
fi

# [N] Wrong Password
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"wrongpass\"}")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Wrong Password (401)"
else
    fail "[N] Wrong Password expected 401" "got $CODE"
fi

# [N] Non-existent User
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"nonexistent_xyz","password":"TestPass123!"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Non-existent User (401)"
else
    fail "[N] Non-existent User expected 401" "got $CODE"
fi

# [N] Empty Body
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "400" ]; then
    pass "[N] Empty Body (400)"
else
    fail "[N] Empty Body expected 400" "got $CODE"
fi

# ================================================================
# 3. PROTECTED ENDPOINTS
# ================================================================
section "3. PROTECTED ENDPOINTS"

# [P] Get /auth/me
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    USER_ID=$(echo "$BODY" | jq -r '.id // empty')
    pass "[P] Get /auth/me (200)"
else
    fail "[P] Get /auth/me" "$BODY"
fi

# [N] /auth/me Without Auth
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/me")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] /auth/me Without Auth (401)"
else
    fail "[N] /auth/me Without Auth expected 401" "got $CODE"
fi

# [N] /auth/me Invalid Token
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/me" \
    -H "Authorization: Bearer invalid.token.here")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] /auth/me Invalid Token (401)"
else
    fail "[N] /auth/me Invalid Token expected 401" "got $CODE"
fi

# [P] Get Sessions
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/sessions" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Get Sessions (200)"
else
    fail "[P] Get Sessions" "got $CODE"
fi

# [N] Sessions Without Auth
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/sessions")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Sessions Without Auth (401)"
else
    fail "[N] Sessions Without Auth expected 401" "got $CODE"
fi

# [P] Secure Info
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/secure/info" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Secure Info (200)"
else
    fail "[P] Secure Info" "got $CODE"
fi

# ================================================================
# 4. REFRESH TOKEN TESTS
# ================================================================
section "4. REFRESH TOKEN TESTS"

# [P] Valid Refresh
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -H "X-Device-Id: test-device-001" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    NEW_TOKEN=$(echo "$BODY" | jq -r '.accessToken // empty')
    if [ -n "$NEW_TOKEN" ]; then
        ACCESS_TOKEN="$NEW_TOKEN"
        NEW_REFRESH=$(echo "$BODY" | jq -r '.refreshToken // empty')
        if [ -n "$NEW_REFRESH" ]; then
            REFRESH_TOKEN="$NEW_REFRESH"
        fi
        pass "[P] Valid Refresh (200 + new token)"
    else
        fail "[P] Valid Refresh - no token" "$BODY"
    fi
else
    fail "[P] Valid Refresh" "$BODY"
fi

# [N] Missing Refresh Token
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d '{}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Missing Refresh Token (401)"
else
    fail "[N] Missing Refresh Token expected 401" "got $CODE"
fi

# [N] Invalid Refresh Token
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"invalid-token"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Invalid Refresh Token (401)"
else
    fail "[N] Invalid Refresh Token expected 401" "got $CODE"
fi

# ================================================================
# 5. JWKS TESTS
# ================================================================
section "5. JWKS TESTS"

# [P] Get JWKS
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/keys/jwks.json")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" == "200" ]; then
    KEYS=$(echo "$BODY" | jq -r '.keys // empty')
    if [ -n "$KEYS" ]; then
        pass "[P] Get JWKS (200 + keys)"
    else
        fail "[P] Get JWKS - no keys" "$BODY"
    fi
else
    fail "[P] Get JWKS" "$BODY"
fi

# [P] Get Well-Known JWKS
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/keys/.well-known/jwks.json")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Get Well-Known JWKS (200)"
else
    fail "[P] Get Well-Known JWKS" "got $CODE"
fi

# ================================================================
# 6. ADMIN OPERATIONS
# ================================================================
section "6. ADMIN OPERATIONS"

# [P] Get Audit Logs
RESP=$(curl -s -w "\n%{http_code}" "$ADMIN_URL/admin/audit/logs?page=0&size=10" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Get Audit Logs (200)"
else
    fail "[P] Get Audit Logs" "got $CODE"
fi

# [N] Audit Logs Without Auth
RESP=$(curl -s -w "\n%{http_code}" "$ADMIN_URL/admin/audit/logs")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ] || [ "$CODE" == "403" ]; then
    pass "[N] Audit Logs Without Auth ($CODE)"
else
    fail "[N] Audit Logs Without Auth expected 401/403" "got $CODE"
fi

# [P] Create Admin
NEW_ADMIN="admin_$(date +%s)"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ADMIN_URL/admin/create" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"username\":\"$NEW_ADMIN\",\"password\":\"NewAdmin123!\"}")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Create Admin (200)"
else
    fail "[P] Create Admin" "got $CODE"
fi

# [N] Create Admin Without Auth
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ADMIN_URL/admin/create" \
    -H "Content-Type: application/json" \
    -d '{"username":"newadmin","password":"NewAdmin123!"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Create Admin Without Auth (401)"
else
    fail "[N] Create Admin Without Auth expected 401" "got $CODE"
fi

# [N] Admin Login Wrong Password
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ADMIN_URL/admin/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongpass"}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Admin Login Wrong Password (401)"
else
    fail "[N] Admin Login Wrong Password expected 401" "got $CODE"
fi

# ================================================================
# 7. LOGOUT TEST
# ================================================================
section "7. LOGOUT TEST"

# [P] Valid Logout
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/logout" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "200" ]; then
    pass "[P] Valid Logout (200)"
else
    fail "[P] Valid Logout" "got $CODE"
fi

# [N] Logout Missing Refresh Token
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/logout" \
    -H "Content-Type: application/json" \
    -d '{}')
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Logout Missing Refresh Token (401)"
else
    fail "[N] Logout Missing Refresh Token expected 401" "got $CODE"
fi

# ================================================================
# 8. SECURITY TESTS
# ================================================================
section "8. SECURITY TESTS"

# [N] Gateway Blocks Admin Routes
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/admin/health")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "404" ]; then
    pass "[N] Gateway Blocks Admin Routes (404)"
else
    fail "[N] Gateway Blocks Admin Routes expected 404" "got $CODE"
fi

# [N] Tampered JWT
RESP=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/auth/me" \
    -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.invalid")
CODE=$(echo "$RESP" | tail -1)
if [ "$CODE" == "401" ]; then
    pass "[N] Tampered JWT (401)"
else
    fail "[N] Tampered JWT expected 401" "got $CODE"
fi

# ================================================================
# SUMMARY
# ================================================================
section "SUMMARY"
echo ""
echo -e "Total Passed: ${GREEN}$PASSED${NC}"
echo -e "Total Failed: ${RED}$FAILED${NC}"
echo ""
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}⚠️  Some tests failed. Review above for details.${NC}"
    exit 1
fi
