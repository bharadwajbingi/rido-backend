#!/bin/bash
source ./common.sh

section "14 - Admin Port Isolation Tests"

# 1. Check Admin Login on Main Port
echo -n "Checking /admin/login on Main Port ($AUTH_URL)... "
# We assume POST to /admin/login with no body = 400 (Bad Request) if reached, 404/403 if blocked
CODE_MAIN=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$AUTH_URL/admin/login")

if [ "$CODE_MAIN" == "404" ] || [ "$CODE_MAIN" == "403" ]; then
    pass "Admin Login NOT found on Main Port ($CODE_MAIN)"
elif [ "$CODE_MAIN" == "400" ]; then
    fail "Admin Login EXPOSED on Main Port" "Got 400 (Bad Request), implies endpoint exists and parsed request."
elif [ "$CODE_MAIN" == "200" ]; then
    fail "Admin Login EXPOSED on Main Port" "Got 200 OK!"
else
    fail "Admin Login Accessible on Main Port?" "Got $CODE_MAIN"
fi

# 2. Check Admin Login on Admin Port (Should Succeed to find endpoint)
echo -n "Checking /admin/login on Admin Port ($ADMIN_URL)... "
CODE_ADMIN=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ADMIN_URL/admin/login" -H "$CONTENT_TYPE" -d '{"username":"","password":""}')

if [ "$CODE_ADMIN" == "400" ] || [ "$CODE_ADMIN" == "200" ] || [ "$CODE_ADMIN" == "401" ]; then
    pass "Admin Login Found on Admin Port ($CODE_ADMIN)"
else
    fail "Admin Login unreachable on Admin Port?" "Got $CODE_ADMIN"
fi

section "Admin Port Tests Complete"
