#!/bin/bash
echo "=== 06 - Access Token Validation Tests ==="
BASE_URL="http://localhost:8080"
TEST_USER="accesstest_$(uuidgen | cut -d'-' -f1)"

echo "📋 Setup: Register & Login"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}")
ACCESS_TOKEN=$(echo "$LOGIN" | jq -r '.accessToken')
echo ""

echo "✅ Test 1: Valid Token - Access Allowed"
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq
echo ""

echo "❌ Test 2: Missing Token"
curl -s -X GET "$BASE_URL/auth/me" | jq
echo ""

echo "❌ Test 3: Expired Token"
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE2MDAwMDAwMDB9.fake" | jq
echo ""

echo "❌ Test 4: Tampered Signature"
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.tampered" | jq
echo ""

echo "✅ Access token tests complete!"
