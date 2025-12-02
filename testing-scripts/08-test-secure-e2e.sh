#!/bin/bash

BASE="http://localhost:8081"
ADMIN_SETUP_KEY="InternalSecretKey"

echo "===================================================="
echo "1) WAIT FOR AUTH SERVICE TO START"
echo "===================================================="
sleep 5

echo "===================================================="
echo "2) CHECK FIRST ADMIN (bootstrap)"
echo "===================================================="
psql -U rh_user -d ride_hailing -c "SELECT username, role FROM users;"

echo "===================================================="
echo "3) REGISTER NORMAL USER"
echo "===================================================="
curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}'
echo -e "\n"

echo "===================================================="
echo "4) LOGIN NORMAL USER"
echo "===================================================="
USER_TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}' \
  | jq -r '.accessToken')

echo "USER_TOKEN = $USER_TOKEN"
echo

echo "===================================================="
echo "5) VERIFY NORMAL USER CANNOT ROTATE KEYS"
echo "===================================================="
curl -i -X POST "$BASE/auth/keys/rotate" \
  -H "Authorization: Bearer $USER_TOKEN"
echo -e "\n"

echo "===================================================="
echo "6) CREATE OPS ADMIN VIA INTERNAL ENDPOINT"
echo "===================================================="
curl -s -X POST "$BASE/internal/admin/create" \
  -H "X-SYSTEM-KEY: $ADMIN_SETUP_KEY" \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"Ops@123"}'
echo -e "\n"

echo "===================================================="
echo "7) LOGIN OPS ADMIN"
echo "===================================================="
OPS_TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"Ops@123"}' \
  | jq -r '.accessToken')

echo "OPS_TOKEN = $OPS_TOKEN"
echo

echo "===================================================="
echo "8) VERIFY ADMIN TOKEN CONTAINS ROLES=[ADMIN]"
echo "===================================================="
echo "$OPS_TOKEN" | cut -d '.' -f2 | base64 -d 2>/dev/null
echo -e "\n"

echo "===================================================="
echo "9) ROTATE SIGNING KEY USING OPS ADMIN"
echo "===================================================="
curl -i -X POST "$BASE/auth/keys/rotate" \
  -H "Authorization: Bearer $OPS_TOKEN"
echo -e "\n"

echo "===================================================="
echo "10) TRY ROTATE AGAIN USING NORMAL USER"
echo "===================================================="
curl -i -X POST "$BASE/auth/keys/rotate" \
  -H "Authorization: Bearer $USER_TOKEN"
echo -e "\n"

echo "===================================================="
echo "11) TEST REFRESH TOKEN FLOW"
echo "===================================================="
REFRESH=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}' \
  | jq -r '.refreshToken')

echo "REFRESH = $REFRESH"
echo

curl -s -X POST "$BASE/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
echo -e "\n"

echo "===================================================="
echo "12) TEST LOGOUT (blacklist JTI + revoke refresh)"
echo "===================================================="

USER_LOGIN_JSON=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}')

ACCESS=$(echo "$USER_LOGIN_JSON" | jq -r '.accessToken')
REF=$(echo "$USER_LOGIN_JSON" | jq -r '.refreshToken')

curl -s -X POST "$BASE/auth/logout" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REF\"}"
echo -e "\n"

echo "===================================================="
echo "13) TEST BLACKLIST: ACCESS TOKEN MUST NOW FAIL"
echo "===================================================="
curl -i -X GET "http://localhost:8080/auth/me" \
  -H "Authorization: Bearer $ACCESS"
echo -e "\n"

echo "===================================================="
echo "ALL TESTS COMPLETED"
echo "===================================================="
