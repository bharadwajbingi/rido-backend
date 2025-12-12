#!/bin/bash
source ./common.sh

section "02 - Functional Tests (Happy Path)"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="func_user_${RANDOM_NUM}"
PASSWORD="Password123!"

# 1. Register
echo "Registering user $USERNAME..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_02_1

assert_status /tmp/res_02_1 "200" "Registration Success"

# 2. Check Username
curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/check-username?username=$USERNAME" > /tmp/res_02_2
assert_status /tmp/res_02_2 "200" "Check Username Exists"
# Verify body says available: false
if grep -q '"available":false' /tmp/res_02_2; then
    pass "Username correctly marked unavailable"
else
    fail "Username availability check failed"
fi

# 3. Login
echo "Logging in..."
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}" > /tmp/res_02_3

assert_status /tmp/res_02_3 "200" "Login Success"

ACCESS_TOKEN=$(json_val "$(head -n -1 /tmp/res_02_3)" "accessToken")
REFRESH_TOKEN=$(json_val "$(head -n -1 /tmp/res_02_3)" "refreshToken")

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
    fail "Login response missing accessToken"
fi

pass "Got Access Token"

# 4. Get Me
curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN" > /tmp/res_02_4

assert_status /tmp/res_02_4 "200" "Get Me Success"

# 5. List Sessions
curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/sessions" \
    -H "Authorization: Bearer $ACCESS_TOKEN" > /tmp/res_02_5

assert_status /tmp/res_02_5 "200" "List Sessions Success"

# 6. Logout
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/logout" \
    -H "$CONTENT_TYPE" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}" > /tmp/res_02_6

assert_status /tmp/res_02_6 "200" "Logout Success"

section "Functional Tests Complete"
