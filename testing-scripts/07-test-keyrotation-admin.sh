#!/bin/bash
set -e

AUTH="http://localhost:8081"
GW="http://localhost:8080"
KEY="InternalSecretKey"

echo ""
echo "==============================================="
echo "STEP 0 — WAIT FOR AUTH SERVICE"
echo "==============================================="
sleep 2

# =====================================================
#   STEP 1: CREATE OPS ADMIN
# =====================================================
echo ""
echo "==============================================="
echo "STEP 1 — CREATE OPS ADMIN (idempotent)"
echo "==============================================="

curl -s -X POST "$AUTH/internal/admin/create" \
  -H "X-SYSTEM-KEY: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"Ops@123"}'

echo ""


# =====================================================
#   STEP 2: LOGIN ADMIN
# =====================================================
echo ""
echo "==============================================="
echo "STEP 2 — LOGIN AS OPS ADMIN"
echo "==============================================="

OPS_TOKEN=$(curl -s -X POST "$AUTH/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"Ops@123"}' | jq -r '.accessToken')

echo "OPS_TOKEN = $OPS_TOKEN"
echo ""

echo "==============================================="
echo "STEP 2.1 — DECODE ADMIN JWT"
echo "==============================================="
curl -s -X POST "$AUTH/auth/decode" \
  -H "Authorization: Bearer $OPS_TOKEN"
echo ""


# =====================================================
#   STEP 3: KEY ROTATION — MUST WORK FOR ADMIN
# =====================================================
echo ""
echo "==============================================="
echo "STEP 3 — ROTATE KEY AS ADMIN (THROUGH GATEWAY)"
echo "==============================================="

curl -i -s -X POST "$GW/auth/keys/rotate" \
  -H "Authorization: Bearer $OPS_TOKEN"
echo ""


# =====================================================
#   STEP 4: REGISTER MULTIPLE USERS
# =====================================================
echo ""
echo "==============================================="
echo "STEP 4 — REGISTER 3 NORMAL USERS"
echo "==============================================="

USERS=("user1" "user2" "user3")

for u in "${USERS[@]}"; do
  curl -s -X POST "$AUTH/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$u\",\"password\":\"pass123\"}"
  echo ""
done


# =====================================================
#   STEP 5: LOGIN NORMAL USER AND TEST PERMISSIONS
# =====================================================
echo ""
echo "==============================================="
echo "STEP 5 — LOGIN NORMAL USER"
echo "==============================================="

USER_TOKEN=$(curl -s -X POST "$AUTH/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}' \
  | jq -r '.accessToken')

echo "USER_TOKEN = $USER_TOKEN"


echo ""
echo "==============================================="
echo "STEP 5.1 — USER TRY TO ROTATE (SHOULD FAIL)"
echo "==============================================="
curl -i -s -X POST "$GW/auth/keys/rotate" \
  -H "Authorization: Bearer $USER_TOKEN"
echo ""


# =====================================================
#  STEP 6: REFRESH TOKEN FLOW
# =====================================================
echo ""
echo "==============================================="
echo "STEP 6 — REFRESH TOKEN FLOW"
echo "==============================================="

REFRESH=$(curl -s -X POST "$AUTH/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass123"}' \
  | jq -r '.refreshToken')

echo "REFRESH = $REFRESH"

echo ""
NEW_ACCESS=$(curl -s -X POST "$AUTH/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r '.accessToken')

echo "NEW_ACCESS = $NEW_ACCESS"


# =====================================================
#  STEP 7: LOGOUT — BLACKLIST ACCESS TOKEN
# =====================================================
echo ""
echo "==============================================="
echo "STEP 7 — LOGOUT USER (BLACKLIST JTI)"
echo "==============================================="

curl -s -X POST "$AUTH/auth/logout" \
  -H "Authorization: Bearer $NEW_ACCESS"

echo ""


# =====================================================
#  STEP 8: BLACKLIST TEST — ACCESS SHOULD FAIL
# =====================================================
echo ""
echo "==============================================="
echo "STEP 8 — BLACKLIST CHECK (ACCESS MUST FAIL)"
echo "==============================================="

curl -i -s -X GET "$GW/auth/test-protected" \
  -H "Authorization: Bearer $NEW_ACCESS"
echo ""


# =====================================================
#  STEP 9: JWKS MUST BE PUBLIC
# =====================================================
echo ""
echo "==============================================="
echo "STEP 9 — JWKS PUBLIC ENDPOINT"
echo "==============================================="

curl -i -s "$GW/auth/keys/jwks.json"
echo ""


# =====================================================
#  END
# =====================================================
echo ""
echo "==============================================="
echo "🎉 ALL E2E TESTS COMPLETED"
echo "==============================================="

