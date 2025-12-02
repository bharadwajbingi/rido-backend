#!/bin/bash
echo "=== 07 - Roles & Authorization Tests ==="
BASE_URL="http://localhost:8080"

echo "✅ Test 1: Public Endpoint (JWKS)"
curl -s -X GET "$BASE_URL/auth/keys/jwks.json" | jq
echo ""

echo "📋 Test 2: Try Admin Login"
ADMIN_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"SuperSecretAdmin123"}')
echo "$ADMIN_LOGIN" | jq
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | jq -r '.accessToken')
echo ""

echo "✅ Test 3: Admin Endpoint (if admin exists)"
if [ "$ADMIN_TOKEN" != "null" ]; then
  curl -s -X POST "$BASE_URL/auth/keys/rotate" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | jq
else
  echo "Admin user not available"
fi
echo ""

echo "✅ Roles & Authorization tests complete!"
