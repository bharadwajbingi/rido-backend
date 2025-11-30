#!/bin/bash

BASE="http://localhost:8080"
AUTH="http://localhost:8081"

call_api() {
  URL=$1
  shift
  echo "→ Calling: $URL"
  RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$URL" "$@")
  BODY=$(echo "$RESPONSE" | sed -e 's/HTTP_STATUS\:.*//')
  STATUS=$(echo "$RESPONSE" | tr -d '\n' | sed -e 's/.*HTTP_STATUS://')
  echo "Status: $STATUS"
  echo "Body:"
  echo "$BODY"
  echo
}

echo "==============================="
echo "1️⃣ JWKS endpoint"
echo "==============================="
call_api "$AUTH/auth/keys/jwks.json"

echo "==============================="
echo "2️⃣ Register user"
echo "==============================="
call_api "$BASE/auth/register" \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"testk","password":"pass123"}'

echo "==============================="
echo "3️⃣ Login"
echo "==============================="
LOGIN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev1" \
  -d '{"username":"testk","password":"pass123"}')

echo "$LOGIN"
ACCESS=$(echo "$LOGIN" | jq -r ".accessToken")
REFRESH=$(echo "$LOGIN" | jq -r ".refreshToken")
echo "ACCESS = $ACCESS"
echo "REFRESH = $REFRESH"
echo

echo "==============================="
echo "4️⃣ /me endpoint (before logout)"
echo "==============================="
call_api "$BASE/auth/me" \
  -H "Authorization: Bearer $ACCESS"

echo "==============================="
echo "5️⃣ Logout"
echo "==============================="
call_api "$BASE/auth/logout" \
  -X POST \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"

echo "==============================="
echo "6️⃣ /me endpoint (after logout — should fail)"
echo "==============================="
call_api "$BASE/auth/me" \
  -H "Authorization: Bearer $ACCESS"

echo "==============================="
echo "7️⃣ Key rotation"
echo "==============================="
call_api "$AUTH/auth/keys/rotate" -X POST
NEW_LOGIN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev1" \
  -d '{"username":"testk","password":"pass123"}')
NEW_ACCESS=$(echo "$NEW_LOGIN" | jq -r ".accessToken")

echo "New Token Header:"
echo "$NEW_ACCESS" | cut -d '.' -f1 | base64 -d 2>/dev/null | jq .
echo

echo "==============================="
echo "8️⃣ Backward compatibility test (OLD token)"
echo "==============================="
call_api "$BASE/auth/me" \
  -H "Authorization: Bearer $ACCESS"

echo "==============================="
echo "🔥 FULL TEST COMPLETE"
echo "==============================="
