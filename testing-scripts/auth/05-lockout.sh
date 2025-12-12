#!/bin/bash
source ./common.sh

section "05 - Account Lockout Tests"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="lockout_user_${RANDOM_NUM}"
PASSWORD="Password123!"
WRONG_PASS="WrongPassword123!"

register_user "$USERNAME" "$PASSWORD" || fail "Registration failed"
pass "User Registered: $USERNAME"

echo "Attempting 5 invalid logins..."
for i in {1..5}; do
    curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$USERNAME\", \"password\": \"$WRONG_PASS\"}" > /tmp/lockout_loop

    CODE=$(tail -n1 /tmp/lockout_loop)
    
    # 400/401 acceptable for invalid credentials
    # 423/429 acceptable if lockout triggers early (eager protection)
    if [ "$CODE" == "401" ] || [ "$CODE" == "400" ]; then
        echo "Attempt $i: Failed as expected ($CODE)"
    elif [ "$CODE" == "423" ] || [ "$CODE" == "429" ]; then
        echo "Attempt $i: Locked Early ($CODE) - Acceptable"
    else
        fail "Unexpected code during fail attempts: $CODE"
    fi
done

echo "Attempting 6th login with CORRECT password..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_05

CODE=$(tail -n1 /tmp/res_05)
BODY=$(head -n -1 /tmp/res_05)

if [ "$CODE" == "401" ] || [ "$CODE" == "423" ] || [ "$CODE" == "429" ]; then
    pass "Account locked successfully (Code: $CODE)"
elif [[ "$BODY" == *"Lock"* ]] || [[ "$BODY" == *"lock"* ]]; then
    pass "Account locked (Verified by Body content, Code: $CODE)"
else
    # Check if we were already locked early
    # If the last loop attempt was 423, we are good.
    # But hard to track state here without variable.
    # We fail normally.
    fail "Account Lockout Failed" "Expected 401/423/429, got $CODE. Body: $BODY"
fi

section "Lockout Tests Complete"
