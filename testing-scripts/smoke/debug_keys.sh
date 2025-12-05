#!/bin/bash
# Debug Keys

GATEWAY_URL="http://localhost:8080"
echo "=== Debug Keys ==="

# Register
USER="testkeys$(date +%s)"
curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Pass123!\"}" > /dev/null

# Login
RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Pass123!\",\"deviceId\":\"dev1\"}")

TOKEN=$(echo "$RESPONSE" | jq -r '.accessToken')
echo "Token: $TOKEN"

# Decode Header
HEADER=$(echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null)
echo "Header: $HEADER"

KID=$(echo "$HEADER" | jq -r '.kid')
echo "Token KID: $KID"

# Get JWKS
JWKS=$(curl -s "$GATEWAY_URL/auth/keys/jwks.json")
echo "JWKS: $JWKS"

if echo "$JWKS" | grep -q "$KID"; then
    echo "✅ KID found in JWKS"
else
    echo "❌ KID NOT found in JWKS"
fi

# Try sessions
SESSIONS=$(curl -v -X GET "$GATEWAY_URL/auth/sessions" \
  -H "Authorization: Bearer $TOKEN" 2>&1)

echo "Sessions response code:"
echo "$SESSIONS" | grep "< HTTP"
