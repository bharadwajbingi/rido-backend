#!/bin/bash

# Config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
AUTH_URL="https://localhost:8081"
CERT_DIR="$PROJECT_ROOT/infra/mtls-certs"
GATEWAY_CRT="$CERT_DIR/gateway/gateway.crt"
GATEWAY_KEY="$CERT_DIR/gateway/gateway.key"
CA_CRT="$CERT_DIR/ca/ca.crt"

BOOTSTRAP_ADMIN="admin"
BOOTSTRAP_PASS="SuperSecretAdmin123"

NEW_ADMIN_USER="new_admin_$(date +%s)"
NEW_ADMIN_PASS="NewAdminPass123!"

NORMAL_USER="normal_user_$(date +%s)"
NORMAL_PASS="UserPass123!"

# Helper for curl with mTLS
curl_mtls() {
  curl -s -k --cert "$GATEWAY_CRT" --key "$GATEWAY_KEY" "$@"
}

echo "----------------------------------------------------------------"
echo "1. Login as Bootstrap Admin (via Internal Endpoint)"
echo "----------------------------------------------------------------"
LOGIN_RESP=$(curl_mtls -X POST "$AUTH_URL/internal/auth/admin-login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$BOOTSTRAP_ADMIN\",
    \"password\": \"$BOOTSTRAP_PASS\"
  }")

ADMIN_TOKEN=$(echo $LOGIN_RESP | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ Failed to login as bootstrap admin. Response: $LOGIN_RESP"
  exit 1
else
  echo "✅ Logged in as Admin. Token: ${ADMIN_TOKEN:0:10}..."
  
  # Decode Header
  HEADER=$(echo "$ADMIN_TOKEN" | cut -d'.' -f1)
  # Add padding if needed
  REM=$(( ${#HEADER} % 4 ))
  if [ $REM -eq 2 ]; then HEADER="$HEADER=="; fi
  if [ $REM -eq 3 ]; then HEADER="$HEADER="; fi
  
  DECODED_HEADER=$(echo "$HEADER" | base64 -d 2>/dev/null)
  echo "Token Header: $DECODED_HEADER"
fi

echo ""
echo "----------------------------------------------------------------"
echo "2. Create NEW Admin using Bootstrap Admin Token"
echo "----------------------------------------------------------------"
CREATE_RESP=$(curl_mtls -w "\nHTTP_STATUS:%{http_code}" -X POST "$AUTH_URL/admin/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{
    \"username\": \"$NEW_ADMIN_USER\",
    \"password\": \"$NEW_ADMIN_PASS\"
  }")

HTTP_STATUS=$(echo "$CREATE_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
BODY=$(echo "$CREATE_RESP" | grep -v "HTTP_STATUS")

if [ "$HTTP_STATUS" == "200" ]; then
  echo "✅ Admin creation successful. Response: $BODY"
else
  echo "❌ Admin creation failed. Status: $HTTP_STATUS. Response: $BODY"
  exit 1
fi

echo ""
echo "----------------------------------------------------------------"
echo "3. Verify New Admin Can Login (via Internal Endpoint)"
echo "----------------------------------------------------------------"
NEW_LOGIN_RESP=$(curl_mtls -X POST "$AUTH_URL/internal/auth/admin-login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$NEW_ADMIN_USER\",
    \"password\": \"$NEW_ADMIN_PASS\"
  }")

NEW_TOKEN=$(echo $NEW_LOGIN_RESP | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [ -z "$NEW_TOKEN" ]; then
  echo "❌ Failed to login as NEW admin. Response: $NEW_LOGIN_RESP"
  exit 1
else
  echo "✅ Logged in as NEW Admin. Token: ${NEW_TOKEN:0:10}..."
fi

echo ""
echo "----------------------------------------------------------------"
echo "4. Negative Test: Normal User trying to create Admin"
echo "----------------------------------------------------------------"
# Register normal user (Public endpoint, but we need mTLS to talk to Auth service directly)
curl_mtls -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$NORMAL_USER\",
    \"password\": \"$NORMAL_PASS\"
  }" > /dev/null

# Login normal user (Public endpoint)
USER_LOGIN_RESP=$(curl_mtls -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$NORMAL_USER\",
    \"password\": \"$NORMAL_PASS\"
  }")

USER_TOKEN=$(echo $USER_LOGIN_RESP | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [ -z "$USER_TOKEN" ]; then
  echo "❌ Failed to login as normal user."
  exit 1
fi

# Try to create admin
FAIL_RESP=$(curl_mtls -w "\nHTTP_STATUS:%{http_code}" -X POST "$AUTH_URL/admin/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{
    \"username\": \"fail_admin_$(date +%s)\",
    \"password\": \"ShouldFail123!\"
  }")

FAIL_STATUS=$(echo "$FAIL_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)

if [ "$FAIL_STATUS" == "403" ]; then
  echo "✅ Access Denied for Normal User (403 Forbidden) as expected."
else
  echo "❌ Unexpected status for Normal User: $FAIL_STATUS. Should be 403."
  exit 1
fi

echo ""
echo "----------------------------------------------------------------"
echo "🎉 ALL TESTS PASSED"
echo "----------------------------------------------------------------"
