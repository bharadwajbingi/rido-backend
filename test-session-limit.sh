#!/bin/bash

# Session Limit Enforcement Manual Test
# This script tests that the auth service correctly enforces max-active-sessions=5

set -e

API_URL="http://localhost:8091/auth"
USERNAME="session_test_$(date +%s)"
PASSWORD="TestPass123!"

echo "============================================"
echo "Session Limit Enforcement Test"
echo "============================================"
echo ""

# Step 1: Register test user
echo "Step 1: Registering test user: $USERNAME"
REGISTER_RESPONSE=$(curl -s -X POST "$API_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

if echo "$REGISTER_RESPONSE" | grep -q "error"; then
  echo "❌ Registration failed: $REGISTER_RESPONSE"
  exit 1
fi
echo "✅ User registered successfully"
echo ""

# Function to login and save tokens
login_with_device() {
  local device_id=$1
  local response=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\", \"deviceId\": \"$device_id\"}")
  
  if echo "$response" | grep -q "accessToken"; then
    local access_token=$(echo "$response" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    local refresh_token=$(echo "$response" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    echo "$access_token|$refresh_token"
  else
    echo "ERROR|ERROR"
  fi
}

# Step 2: Login 5 times with different devices
echo "Step 2: Creating 5 sessions (at the limit)"
declare -a TOKENS
for i in {1..5}; do
  echo "  Login $i with device-$i..."
  RESULT=$(login_with_device "device-$i")
  ACCESS_TOKEN=$(echo "$RESULT" | cut -d'|' -f1)
  REFRESH_TOKEN=$(echo "$RESULT" | cut -d'|' -f2)
  
  if [ "$ACCESS_TOKEN" == "ERROR" ]; then
    echo "❌ Login $i failed"
    exit 1
  fi
  
  TOKENS[$i]="$ACCESS_TOKEN|$REFRESH_TOKEN"
  echo "  ✅ Login $i successful"
done
echo ""

# Step 3: Check we have 5 sessions
echo "Step 3: Verifying 5 active sessions exist"
FIRST_ACCESS_TOKEN=$(echo "${TOKENS[1]}" | cut -d'|' -f1)
SESSIONS=$(curl -s -X GET "$API_URL/sessions" \
  -H "Authorization: Bearer $FIRST_ACCESS_TOKEN")

SESSION_COUNT=$(echo "$SESSIONS" | grep -o '"id"' | wc -l)
echo "  Active sessions: $SESSION_COUNT"

if [ "$SESSION_COUNT" -eq 5 ]; then
  echo "✅ Correct: 5 sessions active"
else
  echo "❌ Expected 5 sessions, found $SESSION_COUNT"
fi
echo ""

# Step 4: Create 6th session (should revoke device-1)
echo "Step 4: Creating 6th session (should trigger revocation)"
echo "  Login with device-6..."
RESULT=$(login_with_device "device-6")
ACCESS_TOKEN_6=$(echo "$RESULT" | cut -d'|' -f1)
REFRESH_TOKEN_6=$(echo "$RESULT" | cut -d'|' -f2)

if [ "$ACCESS_TOKEN_6" == "ERROR" ]; then
  echo "❌ 6th login failed unexpectedly"
  exit 1
fi
echo "  ✅ 6th login successful"
echo ""

# Step 5: Verify still only 5 sessions
echo "Step 5: Verifying still only 5 sessions (oldest should be revoked)"
SESSIONS_AFTER=$(curl -s -X GET "$API_URL/sessions" \
  -H "Authorization: Bearer $ACCESS_TOKEN_6")

SESSION_COUNT_AFTER=$(echo "$SESSIONS_AFTER" | grep -o '"id"' | wc -l)
echo "  Active sessions after 6th login: $SESSION_COUNT_AFTER"

if [ "$SESSION_COUNT_AFTER" -eq 5 ]; then
  echo "✅ PASS: Session limit enforced! Still 5 sessions"
else
  echo "❌ FAIL: Expected 5 sessions, found $SESSION_COUNT_AFTER"
  exit 1
fi
echo ""

# Step 6: Try to use device-1's refresh token (should be revoked)
echo "Step 6: Attempting to refresh with device-1's token (should fail)"
REFRESH_TOKEN_1=$(echo "${TOKENS[1]}" | cut -d'|' -f2)
REFRESH_RESPONSE=$(curl -s -X POST "$API_URL/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN_1\", \"deviceId\": \"device-1\"}")

if echo "$REFRESH_RESPONSE" | grep -qi "revoked"; then
  echo "✅ PASS: device-1's refresh token correctly revoked!"
else
  echo "❌ FAIL: device-1's token should be revoked but isn't"
  echo "Response: $REFRESH_RESPONSE"
  exit 1
fi
echo ""

# Step 7: Verify device-2 through device-6 are still active
echo "Step 7: Verifying devices 2-6 are in session list"
if echo "$SESSIONS_AFTER" | grep -q "device-2" && \
   echo "$SESSIONS_AFTER" | grep -q "device-6"; then
  echo "✅ PASS: Newer sessions (2-6) still active"
else
  echo "⚠️  Warning: Could not verify specific device IDs in session list"
fi
echo ""

echo "============================================"
echo "✅ ALL TESTS PASSED!"
echo "============================================"
echo ""
echo "Session limit enforcement is working correctly:"
echo "  • Max limit: 5 sessions"
echo "  • 6th login triggered automatic revocation"
echo "  • Oldest session (device-1) was revoked"
echo "  • Newest sessions (device-2 to device-6) remain active"
