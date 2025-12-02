#!/bin/bash
echo "=== 05 - JWKS & Signature Validation Tests ==="
BASE_URL="http://localhost:8080"

echo "✅ Test 1: Fetch JWKS"
curl -s -X GET "$BASE_URL/auth/keys/jwks.json" | jq
echo ""

echo "❌ Test 2: Access /auth/me Without Token"
curl -s -X GET "$BASE_URL/auth/me" | jq
echo ""

echo "❌ Test 3: Access /auth/me With Invalid Token"
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer invalid.jwt.token" | jq
echo ""

echo "❌ Test 4: Access /auth/me With Tampered Signature"
curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake" | jq
echo ""

echo "✅ JWKS tests complete!"
