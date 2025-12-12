#!/bin/bash
source ./common.sh

section "06 - Session Limit Enforcement"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="session_user_${RANDOM_NUM}"
PASSWORD="Password123!"

register_user "$USERNAME" "$PASSWORD" || fail "Registration failed"

echo "Creating 5 sessions..."
for i in {1..5}; do
    TOKEN=$(login_user "$USERNAME" "$PASSWORD")
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        echo "Session $i created."
    else
        fail "Failed to create session $i"
    fi
done

echo "Creating 6th session (Should succeed but revoke oldest)..."
TOKEN6=$(login_user "$USERNAME" "$PASSWORD")
if [ -z "$TOKEN6" ] || [ "$TOKEN6" == "null" ]; then
    fail "6th login failed (Should have succeeded and rotated)"
fi

# Verify active sessions count is 5
# Need to use one of the tokens to query /auth/sessions
# Let's use the newest one
curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/sessions" \
    -H "Authorization: Bearer $TOKEN6" > /tmp/res_06

CODE=$(tail -n1 /tmp/res_06)
BODY=$(head -n -1 /tmp/res_06)

assert_status /tmp/res_06 "200" "List Sessions"

# Count sessions (rudimentary count of "id":)
COUNT=$(echo "$BODY" | grep -o "\"id\":" | wc -l)
echo "Active Sessions verified: $COUNT"

if [ "$COUNT" -le 5 ]; then
    pass "Session count <= 5 ($COUNT)"
else
    fail "Session count exceeded limit ($COUNT > 5)"
fi

section "Session Limit Tests Complete"
