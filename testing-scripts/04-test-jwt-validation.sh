#!/bin/bash

AUTH="http://localhost:8080/auth"
GATEWAY="http://localhost:8081/auth/me"

echo "==============================="
echo "1) Register + Login"
echo "==============================="

USER="jwt_test_$RANDOM"
PASS="pass123"

curl -s -X POST "$AUTH/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq

LOGIN=$(curl -s -X POST "$AUTH/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

echo "$LOGIN" | jq

ACCESS=$(echo "$LOGIN" | jq -r '.accessToken')

if [ -z "$ACCESS" ] || [ "$ACCESS" = "null" ]; then
  echo "❌ Login failed, cannot test JWT validation."
  exit 1
fi

echo "Access Token:"
echo "$ACCESS"
echo


# ======================================================
# Helper function
# ======================================================
test_token() {
  NAME="$1"
  TOKEN="$2"
  CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    "$GATEWAY")
  echo "$NAME → HTTP $CODE"
}


echo "==============================="
echo "2) Test WRONG ISS"
echo "==============================="

test_token "Wrong ISS" "wrong.header.sig"


echo "==============================="
echo "3) Test WRONG AUD"
echo "==============================="

test_token "Wrong AUD" "aud.header.sig"


echo "==============================="
echo "4) Test EXPIRED exp"
echo "==============================="

test_token "Expired Token" "expired.header.sig"


echo "==============================="
echo "5) Test FUTURE nbf"
echo "==============================="

test_token "Future NBF" "futurenbf.header.sig"


echo "==============================="
echo "6) Test WRONG ALG"
echo "==============================="

test_token "Bad Alg (none)" "nonealg.header.sig"


echo "==============================="
echo "7) Test WRONG KID"
echo "==============================="

test_token "Wrong KID" "wrongkid.header.sig"


echo "==============================="
echo "8) Test BLACKLISTED JTI"
echo "==============================="

# calling logout blacklists the token
curl -s -X POST "$AUTH/logout" \
  -H "Authorization: Bearer $ACCESS" >/dev/null

test_token "Blacklisted Token" "$ACCESS"


echo
echo "==============================="
echo "DONE — Invalid tokens must return 401/403"
echo "==============================="
