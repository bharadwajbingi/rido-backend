#!/bin/bash
echo "=== 03 - Refresh Token Tests ==="
BASE_URL="http://localhost:8080"
TEST_USER="refreshtest_$(uuidgen | cut -d'-' -f1)"

echo "📋 Setup: Register & Login"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}")
REFRESH_TOKEN=$(echo "$LOGIN" | jq -r '.refreshToken')
echo ""

echo "✅ Test 1: Valid Refresh"
REFRESH1=$(curl -s -X POST "$BASE_URL/auth/refresh" -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
echo "$REFRESH1" | jq
OLD_REFRESH=$REFRESH_TOKEN
REFRESH_TOKEN=$(echo "$REFRESH1" | jq -r '.refreshToken')
echo ""

echo "❌ Test 2: Replay Attack - Reuse Old Token"
curl -s -X POST "$BASE_URL/auth/refresh" -H "Content-Type: application/json" -d "{\"refreshToken\":\"$OLD_REFRESH\"}" | jq
echo ""

echo "❌ Test 3: Invalid UUID"
curl -s -X POST "$BASE_URL/auth/refresh" -H "Content-Type: application/json" -d '{"refreshToken":"not-a-uuid"}' | jq
echo ""

echo "❌ Test 4: Empty Token"
curl -s -X POST "$BASE_URL/auth/refresh" -H "Content-Type: application/json" -d '{"refreshToken":""}' | jq
echo ""

echo "✅ Refresh token tests complete!"
