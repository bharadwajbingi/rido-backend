#!/bin/bash

AUTH="http://localhost:8081/auth"
SECURE="http://localhost:8080/secure/info"   # protected endpoint

echo "==============================="
echo "1) Register test user"
echo "==============================="

USER="ctx_$RANDOM"
PASS="pass123"

curl -s -X POST "$AUTH/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq
echo

echo "==============================="
echo "2) Login and get access token"
echo "==============================="

LOGIN=$(curl -s -X POST "$AUTH/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

echo "$LOGIN" | jq
echo

ACCESS=$(echo "$LOGIN" | jq -r '.accessToken')

if [ -z "$ACCESS" ] || [ "$ACCESS" = "null" ]; then
  echo "❌ LOGIN FAILED"
  exit 1
fi

echo "Access Token:"
echo "$ACCESS"
echo

echo "==============================="
echo "3) CALL SECURE ENDPOINT"
echo "==============================="

RESP=$(curl -s -X GET "$SECURE" \
  -H "Authorization: Bearer $ACCESS")

echo "Gateway Response:"
echo "$RESP" | jq
echo

echo "==============================="
echo "4) VALIDATION"
echo "==============================="

USER_ID=$(echo "$RESP" | jq -r '.userId')
ROLES=$(echo "$RESP" | jq -r '.roles[0]')

if [ -z "$USER_ID" ] || [ "$USER_ID" = "null" ]; then
  echo "❌ SecurityContext NOT populated"
else
  echo "✅ SecurityContext OK (userId = $USER_ID)"
fi

if [ -z "$ROLES" ] || [ "$ROLES" = "null" ]; then
  echo "❌ Roles NOT propagated"
else
  echo "✅ Roles OK ($ROLES)"
fi

echo
echo "==============================="
echo "DONE — SecurityContext verified."
echo "==============================="
