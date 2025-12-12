#!/bin/bash
source ./common.sh

section "17 - JSON Robustness Tests"

# 1. Very Large Payload (DoS attempt)
# Generate 1MB string
LARGE_STR=$(printf 'a%.0s' {1..10000}) 

curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": \"$LARGE_STR\", \"password\": \"short\"}" > /tmp/res_17_1
    
assert_status /tmp/res_17_1 "400|413" "Large Payload Handling"

# 2. Wrong Data Type
curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
    -H "$CONTENT_TYPE" \
    -d "{\"username\": 12345, \"password\": [1,2]}" > /tmp/res_17_2

assert_status /tmp/res_17_2 "400" "Wrong Data Type Handling"

section "JSON Robustness Tests Complete"
