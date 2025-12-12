#!/bin/bash
# Smoke Test: Basic Authentication Flow (Standalone Mode)
# Quick validation of core auth functionality - Direct Auth access on port 8081

set -e

# Direct Auth service port (standalone mode - no Gateway)
AUTH_URL="${AUTH_URL:-http://localhost:8081}"

echo "=========================================="
echo "Basic Auth Flow Smoke Test (Standalone)"
echo "=========================================="
echo ""

# Wait for Auth service readiness
# Readiness check skipped

# Create unique test user
USERNAME="smoke_test_$(date +%s)"
PASSWORD="Test123Pass!"

# Test 1: Registration
echo "Test 1: User Registration"

# Retry logic for rate limiting
MAX_RETRIES=3
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    REGISTER=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
      -H "Content-Type: application/json" \
      -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")
    
    HTTP_CODE=$(echo "$REGISTER" | tail -1)
    BODY=$(echo "$REGISTER" | head -n -1)
    
    if [ "$HTTP_CODE" == "429" ]; then
        echo "  ⚠️  Rate limited (429) - Rate limiter working correctly!"
        echo "  Waiting 5 seconds before retry ($((RETRY_COUNT + 1))/$MAX_RETRIES)..."
        sleep 5
        RETRY_COUNT=$((RETRY_COUNT + 1))
    elif echo "$BODY" | grep -q "error"; then
        echo "❌ Registration failed: $BODY"
        exit 1
    else
        echo "✅ PASS: User registered successfully"
        break
    fi
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "❌ FAIL: Registration failed after $MAX_RETRIES retries (rate limited)"
    echo "  Note: Rate limiter is working, but test needs longer wait"
    exit 1
fi
echo ""

# Test 2: Login
echo "Test 2: Login with Valid Credentials"
LOGIN=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: SmokeTest/1.0" \
  -H "X-Device-Id: test-device" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

if ! echo "$LOGIN" | grep -q "accessToken"; then
    echo "❌ Login failed: $LOGIN"
    exit 1
fi

ACCESS_TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
REFRESH_TOKEN=$(echo "$LOGIN" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)

echo "✅ PASS: Login successful"
echo "   Access token: ${ACCESS_TOKEN:0:20}..."
echo "   Refresh token: ${REFRESH_TOKEN:0:20}..."
echo ""

# Test 3: Token Refresh
echo "Test 3: Token Refresh"
REFRESH=$(curl -s -X POST "$AUTH_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "User-Agent: SmokeTest/1.0" \
  -H "X-Device-Id: test-device" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

if ! echo "$REFRESH" | grep -q "accessToken"; then
    echo "❌ Token refresh failed: $REFRESH"
    exit 1
fi

NEW_ACCESS_TOKEN=$(echo "$REFRESH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
NEW_REFRESH_TOKEN=$(echo "$REFRESH" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
echo "✅ PASS: Token refreshed successfully"
echo "   New access token: ${NEW_ACCESS_TOKEN:0:20}..."
echo ""

# Test 4: Logout
echo "Test 4: Logout"
LOGOUT=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
  -d "{\"refreshToken\": \"$NEW_REFRESH_TOKEN\"}")

STATUS=$(echo "$LOGOUT" | tail -1)
if [ "$STATUS" != "200" ] && [ "$STATUS" != "204" ]; then
    echo "❌ Logout failed with status: $STATUS"
    exit 1
fi

echo "✅ PASS: Logout successful"
echo ""

echo "=========================================="
echo "✅ ALL TESTS PASSED!"
echo "=========================================="
echo ""
echo "Core authentication flow is working:"
echo "  • Registration ✅"
echo "  • Login ✅"
echo "  • Token Refresh ✅"
echo "  • Logout ✅"
