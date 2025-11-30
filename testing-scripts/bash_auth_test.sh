#!/bin/bash

BASE="http://localhost:8080/auth"
USER="gpt11009"
PASS="super1235"
DEVICE1="device-111"
DEVICE2="device-222"

echo
echo "==============================="
echo "STEP 0: Register Test User"
echo "==============================="
curl -s -X POST "$BASE/register" \
 -H "Content-Type: application/json" \
 -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}"
echo; echo


echo "==============================="
echo "STEP 1: Login → Create Session #1"
echo "==============================="
LOGIN1=$(curl -s -X POST "$BASE/login" \
 -H "Content-Type: application/json" \
 -H "X-Device-Id: $DEVICE1" \
 -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

echo "$LOGIN1"; echo

ACCESS1=$(echo "$LOGIN1" | jq -r '.accessToken')
REFRESH1=$(echo "$LOGIN1" | jq -r '.refreshToken')

USER_ID=$(echo "$ACCESS1" | awk -F '.' '{print $2}' | base64 -d 2>/dev/null | jq -r '.sub')

echo "ACCESS1:  $ACCESS1"
echo "REFRESH1: $REFRESH1"
echo "USER_ID:  $USER_ID"
echo


echo "==============================="
echo "STEP 2: Login → Create Session #2"
echo "==============================="
LOGIN2=$(curl -s -X POST "$BASE/login" \
 -H "Content-Type: application/json" \
 -H "X-Device-Id: $DEVICE2" \
 -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

echo "$LOGIN2"; echo

ACCESS2=$(echo "$LOGIN2" | jq -r '.accessToken')
REFRESH2=$(echo "$LOGIN2" | jq -r '.refreshToken')

echo "ACCESS2:  $ACCESS2"
echo "REFRESH2: $REFRESH2"
echo


echo "==============================="
echo "STEP 3: List Sessions (Expect 2 Sessions)"
echo "==============================="
SESSIONS=$(curl -s -X GET "$BASE/sessions" \
 -H "Authorization: Bearer $ACCESS1" \
 -H "X-User-ID: $USER_ID")

echo "$SESSIONS"; echo


# --------------------------------------------------------------
# 100% CORRECT FIX:
# MATCH refresh tokens to sessions using deviceId
# --------------------------------------------------------------

SESSION1_ID=$(echo "$SESSIONS" | jq -r ".[] | select(.deviceId==\"$DEVICE1\") | .id")
SESSION2_ID=$(echo "$SESSIONS" | jq -r ".[] | select(.deviceId==\"$DEVICE2\") | .id")

echo "SESSION (device-111) = $SESSION1_ID"
echo "SESSION (device-222) = $SESSION2_ID"
echo


echo "==============================="
echo "STEP 4: REVOKE Session for DEVICE1"
echo "==============================="
curl -s -X POST "$BASE/sessions/$SESSION1_ID/revoke" \
 -H "X-User-ID: $USER_ID" \
 -H "Authorization: Bearer $ACCESS1"
echo; echo


echo "==============================="
echo "STEP 5: Verify DEVICE1 Session is Revoked"
echo "==============================="
SESSIONS2=$(curl -s -X GET "$BASE/sessions" \
 -H "X-User-ID: $USER_ID")

echo "$SESSIONS2"; echo


echo "==============================="
echo "STEP 6: Try Using REVOKED Refresh Token #1 → Must be 401"
echo "==============================="
STATUS1=$(curl -s -o /dev/null -w "%{http_code}" \
   -X POST "$BASE/refresh" \
   -H "Content-Type: application/json" \
   -d "{\"refreshToken\":\"$REFRESH1\"}")

echo "Status: $STATUS1"
echo


echo "==============================="
echo "STEP 7: Refresh with Valid Session #2 → Should Work"
echo "==============================="
RESP_REFRESH2=$(curl -s -X POST "$BASE/refresh" \
 -H "Content-Type: application/json" \
 -d "{\"refreshToken\":\"$REFRESH2\"}")

echo "$RESP_REFRESH2"; echo


echo "==============================="
echo "STEP 8: REVOKE ALL SESSIONS"
echo "==============================="
curl -s -X POST "$BASE/sessions/revoke-all" \
 -H "X-User-ID: $USER_ID"
echo; echo


echo "==============================="
echo "STEP 9: Verify NO Active Sessions Left"
echo "==============================="
SESSIONS3=$(curl -s -X GET "$BASE/sessions" \
 -H "X-User-ID: $USER_ID")

echo "$SESSIONS3"; echo


echo "==============================="
echo "STEP 10: Try Using REFRESH2 after Revoke-All → Must be 401"
echo "==============================="
STATUS2=$(curl -s -o /dev/null -w "%{http_code}" \
   -X POST "$BASE/refresh" \
   -H "Content-Type: application/json" \
   -d "{\"refreshToken\":\"$REFRESH2\"}")

echo "Status: $STATUS2"
echo


echo "==============================="
echo "TEST SUITE COMPLETED"
echo "==============================="
