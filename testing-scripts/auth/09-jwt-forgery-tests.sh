#!/bin/bash
source ./common.sh

section "09 - JWT Forgery Tests"

RANDOM_NUM=$((RANDOM % 100000))
USERNAME="jwt_user_${RANDOM_NUM}"
PASSWORD="Password123!"

register_user "$USERNAME" "$PASSWORD"
TOKEN=$(login_user "$USERNAME" "$PASSWORD")

if [ -z "$TOKEN" ]; then
    fail "Setup failed: Could not get token"
fi

pass "Got Valid Token"

# 1. Tampered Signature
# Split token by '.'
HEADER=$(echo "$TOKEN" | cut -d. -f1)
PAYLOAD=$(echo "$TOKEN" | cut -d. -f2)
SIGNATURE=$(echo "$TOKEN" | cut -d. -f3)

# Change last char of signature
BAD_SIG="${SIGNATURE::-1}A"
TAMPERED_TOKEN="$HEADER.$PAYLOAD.$BAD_SIG"

curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
    -H "Authorization: Bearer $TAMPERED_TOKEN" > /tmp/res_09_1

assert_status /tmp/res_09_1 "401" "Tampered Signature Rejected" # Could be 403 or 401

# 2. Alg None Attack (Simulated)
# { "alg": "none" } base64 -> eyJhbGciOiJub25lIn0= (approx)
# This is hard to do properly in bash without base64 tools ensuring no padding etc.
# checking availability of base64
if command -v base64 &> /dev/null; then
    NONE_HEADER=$(echo -n '{"alg":"none","typ":"JWT"}' | base64 | tr -d '=' | tr '/+' '_-')
    NONE_TOKEN="$NONE_HEADER.$PAYLOAD."
    
    curl -s -w "\n%{http_code}" -X GET "$AUTH_URL/auth/me" \
        -H "Authorization: Bearer $NONE_TOKEN" > /tmp/res_09_2
        
    assert_status /tmp/res_09_2 "401" "Alg:None Rejected"
else
    warn "Skipping Alg:None test (base64 not found)"
fi

# 3. Expired Token
# Hard to generate an expired token signed by the server without waiting.
# We will skip this unless we have a dev tool to issue expired tokens.
# Or we can verify the "exp" claim exists in the payload.

echo "Inspecting Payload claims..."
DECODED_PAYLOAD=$(echo "$PAYLOAD" | tr '_-' '/+' | base64 -d 2>/dev/null)
if [[ "$DECODED_PAYLOAD" == *"exp"* ]]; then
    pass "Token contains expiration claim"
else
    warn "Token missing 'exp' claim?"
    echo "Payload: $DECODED_PAYLOAD"
fi

section "JWT Forgery Tests Complete"
