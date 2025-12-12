#!/bin/bash
source ./common.sh

section "19 - Cross Service Metadata Tests"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="meta_user_${RANDOM_NUM}"
PASSWORD="Password123!"
DEVICE_ID="test-device-uuid-1234"
USER_AGENT="Mozilla/5.0 (TestRunner)"

register_user "$USERNAME" "$PASSWORD"

# Login with metadata
echo "Logging in with specific metadata..."
curl -s -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -H "X-Device-Id: $DEVICE_ID" \
    -H "User-Agent: $USER_AGENT" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_19_login

TOKEN=$(json_val "$(cat /tmp/res_19_login)" "accessToken")

# Check Sessions
curl -s -X GET "$AUTH_URL/auth/sessions" \
    -H "Authorization: Bearer $TOKEN" > /tmp/res_19_sessions

BODY=$(cat /tmp/res_19_sessions)

if [[ "$BODY" == *"$DEVICE_ID"* ]]; then
    pass "Device ID stored correctly"
else
    fail "Device ID missing in session"
fi

if [[ "$BODY" == *"$USER_AGENT"* ]]; then
    pass "User Agent stored correctly"
else
    fail "User Agent missing in session"
fi

section "Cross Service Metadata Tests Complete"
