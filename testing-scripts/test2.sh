#!/bin/bash

BASE="http://localhost:8080"
AUTH="http://localhost:8081"

# Helper function
call() {
  echo "============================================"
  echo "→ $1"
  echo "============================================"
  shift
  RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$@" )
  BODY=$(echo "$RESPONSE" | sed -e 's/HTTP_STATUS\:.*//')
  STATUS=$(echo "$RESPONSE" | tr -d '\n' | sed -e 's/.*HTTP_STATUS://')
  echo "Status: $STATUS"
  echo "$BODY"
  echo
}

echo "🚀 TESTING GLOBAL EXCEPTION HANDLER"
echo "===================================================="

# -------------------------------
# 1) INVALID JSON
# -------------------------------
call "Invalid JSON" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"abc","password": }'

# -------------------------------
# 2) INVALID CREDENTIALS
# -------------------------------
call "Invalid Credentials" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"abc","password":"wrong"}'

# -------------------------------
# 3) ACCOUNT LOCK (5 wrong attempts)
# -------------------------------
echo "🔐 Forcing account lock..."

for i in {1..6}; do
  echo "Attempt $i:"
  curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"lockme","password":"wrong"}'
  echo
done

# -------------------------------
# 4) LOGIN WITH LOCKED ACCOUNT
# -------------------------------
call "Account locked" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"lockme","password":"wrong"}'

# -------------------------------
# 5) TOKEN EXPIRED TEST
# (User must provide an expired token manually)
# -------------------------------
EXPIRED_TOKEN="<PUT-AN-EXPIRED-TOKEN-HERE>"

call "Expired Token" -X GET "$BASE/auth/me" \
  -H "Authorization: Bearer $EXPIRED_TOKEN"


# -------------------------------
# 6) REFRESH REPLAY TEST
# -------------------------------
echo "🔁 Testing refresh replay logic (double use)"

# Register temp user
curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"replayuser","password":"p1"}' >/dev/null

# Login to get refresh
LOGIN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"replayuser","password":"p1"}')

REF=$(echo $LOGIN | jq -r ".refreshToken")

call "Refresh #1" -X POST "$BASE/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REF\"}"

call "Refresh Replay (should fail)" -X POST "$BASE/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REF\"}"

echo
echo "===================================================="
echo "🚀 TESTING RATE LIMITING"
echo "===================================================="

# -------------------------------
# 7) LOGIN RATE LIMIT (5/min)
# -------------------------------
echo "🔥 Testing /login rate limit (6 attempts)"

for i in {1..6}; do
  echo -n "Attempt $i → "
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"someone","password":"wrong"}'
done

echo
echo "Expected: attempts 1–5 = 401, attempt 6 = 429"
echo

# -------------------------------
# 8) REGISTER RATE LIMIT (10/min)
# -------------------------------
echo "🔥 Testing /register rate limit (12 attempts)"

for i in {1..12}; do
  echo -n "Register $i → "
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST "$BASE/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"ratetest$i\",\"password\":\"p1\"}"
done

echo
echo "Expected: attempts 1–10 = 200, attempts 11–12 = 429"
echo

# -------------------------------
# 9) REFRESH RATE LIMIT (20/min)
# -------------------------------
echo "🔥 Testing /refresh rate limit (25 attempts)"

# Create new user to get fresh refresh token
curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"rateRefresh","password":"p1"}' >/dev/null

LOGIN2=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"rateRefresh","password":"p1"}')

RRT=$(echo $LOGIN2 | jq -r ".refreshToken")

for i in {1..25}; do
  echo -n "Refresh $i → "
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$RRT\"}"
done

echo
echo "Expected: 1–20 = 200, 21–25 = 429"
echo

echo "===================================================="
echo "🔥 FULL EXCEPTION + RATE LIMITING TEST COMPLETE"
echo "===================================================="
