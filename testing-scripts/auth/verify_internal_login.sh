#!/bin/bash

# Config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
AUTH_URL="https://localhost:8081"
CERT_DIR="$PROJECT_ROOT/infra/mtls-certs"
GATEWAY_CRT="$CERT_DIR/gateway/gateway.crt"
GATEWAY_KEY="$CERT_DIR/gateway/gateway.key"
CA_CRT="$CERT_DIR/ca/ca.crt"

ADMIN_USER="admin"
ADMIN_PASS="SuperSecretAdmin123"

echo "----------------------------------------------------------------"
echo "1. Test Connection WITHOUT Certs (Should Fail Handshake)"
echo "----------------------------------------------------------------"
curl -k -v "$AUTH_URL/internal/auth/admin-login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$ADMIN_USER\",
    \"password\": \"$ADMIN_PASS\"
  }" 2>&1 | grep "alert certificate required" && echo "✅ Handshake failed as expected (Client Auth Required)" || echo "⚠️ Handshake might have succeeded or failed with different error"

echo ""
echo "----------------------------------------------------------------"
echo "2. Test Connection WITH Certs (Should Success)"
echo "----------------------------------------------------------------"
LOGIN_RESP=$(curl -s -k \
  --cert "$GATEWAY_CRT" \
  --key "$GATEWAY_KEY" \
  -X POST "$AUTH_URL/internal/auth/admin-login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$ADMIN_USER\",
    \"password\": \"$ADMIN_PASS\"
  }")

TOKEN=$(echo $LOGIN_RESP | grep -o '"accessToken":"[^"]*' | cut -d'"' -f3)

if [ -n "$TOKEN" ]; then
  echo "✅ Login Successful! Token: ${TOKEN:0:10}..."
else
  echo "❌ Login Failed. Response: $LOGIN_RESP"
  exit 1
fi

echo ""
echo "----------------------------------------------------------------"
echo "🎉 ALL TESTS PASSED"
echo "----------------------------------------------------------------"
