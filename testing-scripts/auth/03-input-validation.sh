#!/bin/bash
source ./common.sh

section "03 - Input Validation Tests"

# 1. Register with bad password (too short)
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"validUser\", \"password\": \"123\"}" > /tmp/res_03_1

assert_status /tmp/res_03_1 "400" "Register Short Password"

# 2. Register with empty username
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"\", \"password\": \"Password123!\"}" > /tmp/res_03_2

assert_status /tmp/res_03_2 "400" "Register Empty Username"

# 3. Login with missing fields
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"\"}" > /tmp/res_03_3

assert_status /tmp/res_03_3 "400" "Login Missing Password"

# 4. Check Username with Null Chars (Security)
curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/check-username?username=bad%00user" > /tmp/res_03_4
assert_status /tmp/res_03_4 "400" "Username Null Byte Injection"

# 5. Malformed JSON
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{ bad_json: " > /tmp/res_03_5

assert_status /tmp/res_03_5 "400" "Malformed JSON"

section "Input Validation Tests Complete"
