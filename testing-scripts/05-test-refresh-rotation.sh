#!/bin/bash

BASE="http://localhost:8080/auth"

echo "==============================="
echo "1) Register test user"
echo "==============================="

USER="rot_test_$RANDOM"
PASS="pass123"

curl -s -X POST "$BASE/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq

echo
echo "==============================="
echo "2) Login & get first refresh token (RT1)"
echo "==============================="

LOGIN=$(curl -s -X POST "$BASE/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

echo "$LOGIN" | jq

RT1=$(echo "$LOGIN" | jq -r '.refreshToken')
echo "RT1 = $RT1"

echo
echo "==============================="
echo "3) Use RT1 to refresh → get RT2"
echo "==============================="

REF1=$(curl -s -X POST "$BASE/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$RT1\"}")

echo "$REF1" | jq

RT2=$(echo "$REF1" | jq -r '.refreshToken')
echo "RT2 = $RT2"

echo
echo "==============================="
echo "4) Attempt replay of RT1 (should fail!)"
echo "==============================="

REPLAY=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$RT1\"}")

echo "Replay attempt HTTP status = $REPLAY"

if [ "$REPLAY" = "401" ] || [ "$REPLAY" = "403" ]; then
  echo "✅ Replay detection WORKING"
else
  echo "❌ Replay detection FAILED"
fi

echo
echo "==============================="
echo "5) Validate RT2 works normally"
echo "==============================="

RT2_TEST=$(curl -s -X POST "$BASE/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$RT2\"}")

echo "$RT2_TEST" | jq
