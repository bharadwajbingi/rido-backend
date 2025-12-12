#!/bin/bash
source ./common.sh

section "18 - Data Integrity Tests"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="integrity_user_${RANDOM_NUM}"
PASSWORD="Password123!"

register_user "$USERNAME" "$PASSWORD" || fail "Initial Register Failed"

# Try duplicate register
echo "Registering SAME user again..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_18_1
    
assert_status /tmp/res_18_1 "400|409" "Duplicate User Rejected"

section "Data Integrity Tests Complete"
