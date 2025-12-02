#!/bin/bash
echo "=== 04 - Logout Tests ==="
BASE_URL="http://localhost:8080"
TEST_USER="logouttest_$(uuidgen | cut -d'-' -f1)"

echo "📋 Setup: Register & Login"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}")
ACCESS_TOKEN=$(echo "$LOGIN" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN" | jq -r '.refreshToken')
echo ""

echo "✅ Test 1: Valid Logout"
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq
echo ""

echo "✅ Test 2: Logout Twice (Idempotent)"
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq
echo ""

echo "❌ Test 3: Logout Without Token"
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq
echo ""

echo "✅ Logout tests complete!"
