#!/bin/bash

echo "=== 01 - Registration Tests ==="
echo ""

BASE_URL="http://localhost:8080"
TEST_USER="regtest_$(uuidgen | cut -d'-' -f1)"

echo "✅ Test 1: Valid Registration"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "❌ Test 2: Duplicate Username"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "❌ Test 3: Weak Password (too short)"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"user_weak","password":"123"}' | jq
echo ""

echo "❌ Test 4: Missing Username"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"password":"SecurePass123!"}' | jq
echo ""

echo "❌ Test 5: Missing Password"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"user_nopass\"}" | jq
echo ""

echo "🛡️ Test 6: SQL Injection - admin' OR '1'='1"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin'\'' OR '\''1'\''='\''1","password":"test"}' | jq
echo ""

echo "🛡️ Test 7: XSS Attack - <script>alert('xss')</script>"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"<script>alert(\"xss\")</script>","password":"test"}' | jq
echo ""

echo "❌ Test 8: Empty Username"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"","password":"SecurePass123!"}' | jq
echo ""

echo "❌ Test 9: Very Long Username"
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"'$(printf 'a%.0s' {1..100})'","password":"SecurePass123!"}' | jq
echo ""

echo "✅ Registration tests complete!"
