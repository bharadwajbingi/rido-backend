#!/bin/bash

echo "=== 02 - Login Tests ==="
echo ""

BASE_URL="http://localhost:8080"
TEST_USER="logintest_$(uuidgen | cut -d'-' -f1)"
TEST_PASS="SecurePass123!"

echo "📋 Setup: Register test user"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}" | jq
echo ""

echo "✅ Test 1: Valid Login"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}")
echo "$LOGIN_RESP" | jq
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.accessToken')
echo ""

echo "❌ Test 2: Wrong Password"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"WrongPassword\"}" | jq
echo ""

echo "❌ Test 3: Wrong Username"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"nonexistentuser","password":"test"}' | jq
echo ""

echo "🛡️ Test 4: SQL Injection in Username"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin'\'' OR '\''1'\''='\''1","password":"test"}' | jq
echo ""

echo "🛡️ Test 5: XSS in Username"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"<script>alert(1)</script>","password":"test"}' | jq
echo ""

echo "❌ Test 6: Missing Username"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"password":"test"}' | jq
echo ""

echo "❌ Test 7: Missing Password"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\"}" | jq
echo ""

echo "❌ Test 8: Malformed JSON"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username": "test"'
echo ""

echo "✅ Login tests complete!"
