#!/bin/bash
source ./common.sh

section "04 - Rate Limiting Tests"

# Add Randomization to avoid collisions
RANDOM_BASE=$((RANDOM % 100000))
BASE_PREFIX="rate_user_${RANDOM_BASE}"

echo "Spamming Register endpoint (Limit: 10/min)..."

for i in {1..12}; do
    USERNAME="${BASE_PREFIX}_$i"
    
    # Capture body for debug
    curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$USERNAME\", \"password\": \"Password123!\"}" > /tmp/rate_res_loop

    CODE=$(tail -n1 /tmp/rate_res_loop)
    BODY=$(head -n -1 /tmp/rate_res_loop)

    echo "Req $i: $CODE"
    
    if [ "$i" -le 10 ]; then
        if [ "$CODE" != "200" ]; then
            warn "Request $i failed with $CODE (Expected 200). Body: $BODY"
        fi
    else
        # 11th and 12th request should be Rate Limited
        # Rate Limiting might return 429 (Standard) or 400/500 depending on config
        # We assume 429.
        if [ "$CODE" == "429" ]; then
            pass "Request $i correctly Rate Limited (429)"
        else
            fail "Rate Limit Failed" "Expected 429 on request $i, got $CODE. Body: $BODY"
        fi
    fi
done

section "Rate Limiting Tests Complete"
