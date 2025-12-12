#!/bin/bash
source ./common.sh

section "07 - Refresh Token Flows"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="refresh_user_${RANDOM_NUM}"
PASSWORD="Password123!"

register_user "$USERNAME" "$PASSWORD"

# Login
echo "Logging in..."
curl -s -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_07_login

REFRESH_TOKEN=$(json_val "$(cat /tmp/res_07_login)" "refreshToken")
ACCESS_TOKEN=$(json_val "$(cat /tmp/res_07_login)" "accessToken")

if [ -z "$REFRESH_TOKEN" ] || [ "$REFRESH_TOKEN" == "null" ]; then
    fail "No refresh token received"
fi

pass "Got Refresh Token"

# 1. Valid Refresh
echo "Refreshing..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
    -H "$CONTENT_TYPE" \
    -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}" > /tmp/res_07_refresh

assert_status /tmp/res_07_refresh "200" "Valid Refresh"

NEW_ACCESS=$(json_val "$(head -n -1 /tmp/res_07_refresh)" "accessToken")
NEW_REFRESH=$(json_val "$(head -n -1 /tmp/res_07_refresh)" "refreshToken")

if [ "$NEW_ACCESS" == "$ACCESS_TOKEN" ]; then
    warn "New Access Token is same as old (Short lived?)"
else
    pass "Got New Access Token"
fi

# 2. Usage of OLD Refresh Token (If rotation enabled, this should fail. If not, it works)
# We won't strict fail on success, but we check behavior.
echo "Attempting to reuse old refresh token..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
    -H "$CONTENT_TYPE" \
    -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}" > /tmp/res_07_reuse

CODE=$(tail -n1 /tmp/res_07_reuse)
if [ "$CODE" == "200" ]; then
    warn "Old Refresh Token still valid (No Rotation?)"
else
    pass "Old Refresh Token Invalidated ($CODE)"
fi

# 3. Invalid Refresh Token
echo "Attempting invalid refresh..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/refresh" \
    -H "$CONTENT_TYPE" \
    -d "{\"refreshToken\": \"invalid-token-string\"}" > /tmp/res_07_bad

assert_status /tmp/res_07_bad "401" "Invalid Refresh Token Rejected"

section "Refresh Token Tests Complete"
