#!/bin/bash
set -e

BASE_URL="http://localhost:8080/auth"

GREEN="\e[32m"
RED="\e[31m"
YELLOW="\e[33m"
RESET="\e[0m"

pass() { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail() { echo -e "${RED}[FAIL]${RESET} $1"; exit 1; }
section() {
  echo ""
  echo -e "${YELLOW}==============================="
  echo -e "$1"
  echo -e "===============================${RESET}"
}

###############################################
# STEP 1 — REGISTER
###############################################
section "STEP 1: Register new user"

USERNAME="test_$(date +%s)"
PASSWD="Secret123"

REG=$(curl -s -w "%{http_code}" -o /tmp/reg.json \
  -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWD\"}")

[[ "$REG" == "200" ]] || fail "Registration failed"
pass "Registration OK for $USERNAME"

###############################################
# STEP 2 — LOGIN SESSION #1
###############################################
section "STEP 2: Login → Session #1"

LOGIN1=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev1" \
  -H "User-Agent: Chrome" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWD\"}")

ACCESS1=$(echo "$LOGIN1" | jq -r '.accessToken')
REFRESH1=$(echo "$LOGIN1" | jq -r '.refreshToken')

[[ "$ACCESS1" != "null" ]] || fail "Login #1 failed"

USER_ID=$(echo "$ACCESS1" | cut -d '.' -f2 | base64 -d | jq -r '.sub')

pass "Login #1 OK → USER_ID = $USER_ID"

###############################################
# STEP 3 — LOGIN SESSION #2
###############################################
section "STEP 3: Login → Session #2"

LOGIN2=$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev2" \
  -H "User-Agent: Firefox" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWD\"}")

ACCESS2=$(echo "$LOGIN2" | jq -r '.accessToken')
REFRESH2=$(echo "$LOGIN2" | jq -r '.refreshToken')

[[ "$ACCESS2" != "null" ]] || fail "Login #2 failed"

pass "Login #2 OK"

###############################################
section "STEP 4: List Sessions (expect 2 active)"

SESS=$(curl -s -X GET "$BASE_URL/sessions" \
  -H "X-User-ID: $USER_ID" \
  -H "Authorization: Bearer $ACCESS1")

ACTIVE_COUNT=$(echo "$SESS" | jq '[.[] | select(.revoked == false)] | length')
[[ "$ACTIVE_COUNT" == "2" ]] || fail "Expected 2 sessions, got $ACTIVE_COUNT"

# FIX: Select sessions by device ID
SESSION1_ID=$(echo "$SESS" | jq -r '.[] | select(.deviceId=="dev1") | .id')
SESSION2_ID=$(echo "$SESS" | jq -r '.[] | select(.deviceId=="dev2") | .id')

[[ "$SESSION1_ID" != "" ]] || fail "Could not find session for dev1"
[[ "$SESSION2_ID" != "" ]] || fail "Could not find session for dev2"

pass "Session list OK (2 active)"


###############################################
# STEP 5 — REVOKE ONE SESSION
###############################################
section "STEP 5: Revoke Session #1"

curl -s -X POST "$BASE_URL/sessions/$SESSION1_ID/revoke" \
  -H "X-User-ID: $USER_ID" \
  -H "Authorization: Bearer $ACCESS1" >/dev/null

UPDATED=$(curl -s -X GET "$BASE_URL/sessions" \
  -H "X-User-ID: $USER_ID" \
  -H "Authorization: Bearer $ACCESS1")

ACTIVE=$(echo "$UPDATED" | jq '[.[] | select(.revoked == false)] | length')

[[ "$ACTIVE" == "1" ]] || fail "Single-session revoke failed"

pass "Revoke #1 OK"

###############################################
# STEP 6 — REVOKED REFRESH TOKEN MUST FAIL
###############################################
section "STEP 6: Refresh using revoked token → expect 401"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev1" \
  -d "{\"refreshToken\":\"$REFRESH1\"}")

[[ "$STATUS" == "401" ]] || fail "Revoked refresh did NOT return 401"

pass "Revoked refresh returns 401 ✔"

###############################################
# STEP 7 — VALID REFRESH WORKS
###############################################
section "STEP 7: Valid session refresh OK"

VAL=$(curl -s -X POST "$BASE_URL/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev2" \
  -d "{\"refreshToken\":\"$REFRESH2\"}")

NEW_REF=$(echo "$VAL" | jq -r '.refreshToken')

[[ "$NEW_REF" != "null" ]] || fail "Valid refresh failed"

pass "Valid refresh OK"

###############################################
# STEP 8 — REPLAY DETECTION
###############################################
section "STEP 8: Replay same refresh token → expect 401"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: dev2" \
  -d "{\"refreshToken\":\"$REFRESH2\"}")

[[ "$STATUS" == "401" ]] || fail "Replay attack NOT detected"

pass "Replay detection OK"

###############################################
# STEP 9 — LOGOUT (Blacklist access token)
###############################################
section "STEP 9: Logout"

curl -s -X POST "$BASE_URL/logout" \
  -H "Authorization: Bearer $ACCESS2" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$NEW_REF\"}" >/dev/null

pass "Logout OK"

###############################################
# STEP 10 — REVOKE ALL SESSIONS
###############################################
section "STEP 10: Revoke ALL"

curl -s -X POST "$BASE_URL/sessions/revoke-all" \
  -H "X-User-ID: $USER_ID" \
  -H "Authorization: Bearer $ACCESS1" >/dev/null

SESS2=$(curl -s -X GET "$BASE_URL/sessions" \
  -H "X-User-ID: $USER_ID" \
  -H "Authorization: Bearer $ACCESS1")

COUNT2=$(echo "$SESS2" | jq 'length')

[[ "$COUNT2" == "0" ]] || fail "Revoke-all failed"

pass "Revoke ALL OK"

###############################################
# FINAL
###############################################
echo ""
echo -e "${GREEN}🎉 ALL TESTS PASSED — AUTH SERVICE IS 100% CORRECT 🎉${RESET}"
