#!/bin/bash
echo "=== 12 - mTLS & Internal Service Auth Tests ==="
BASE_URL="http://localhost:8080"

echo "❌ Test 1: Internal Endpoint Without Key"
curl -s -X POST "$BASE_URL/internal/admin/create" \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' | jq
echo ""

echo "❌ Test 2: Internal Endpoint With Wrong Key"
curl -s -X POST "$BASE_URL/internal/admin/create" \
  -H "Content-Type: application/json" \
  -H "X-SYSTEM-KEY: WrongKey" \
  -d '{"username":"test","password":"test"}' | jq
echo ""

echo "✅ Test 3: Internal Endpoint With Correct Key"
curl -s -X POST "$BASE_URL/internal/admin/create" \
  -H "Content-Type: application/json" \
  -H "X-SYSTEM-KEY: InternalSecretKey" \
  -d "{\"username\":\"admin_$(uuidgen | cut -d'-' -f1)\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "✅ mTLS & Internal auth tests complete!"
