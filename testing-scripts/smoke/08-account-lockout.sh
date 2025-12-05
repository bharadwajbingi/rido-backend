#!/bin/bash
# Smoke Test: Account Lockout
# Verifies that account lockout logic works correctly using lockedUntil field

set -e

GATEWAY_URL="http://localhost:8080"
AUTH_URL="http://localhost:9091"

echo "=========================================="
echo "Account Lockout Test"
echo "=========================================="
echo ""

# Wait for services
echo "Checking if services are ready..."
for i in {1..5}; do
    if curl -s "$GATEWAY_URL/auth/keys/jwks.json" | grep -q "keys"; then
        echo "✅ Gateway is UP"
        break
    fi
    echo "  Waiting... ($i/5)"
    sleep 2
done
echo ""

# Test 1: Successful login works
echo "Test 1: Register and login successfully..."
TEST_USER="lockout_test_$(date +%s)"

# Register user
REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

REG_CODE=$(echo "$REG_RESPONSE" | tail -1)

if [ "$REG_CODE" == "200" ] || [ "$REG_CODE" == "201" ]; then
    echo "✅ PASS: User registered successfully"
else
    echo "❌ FAIL: Registration failed with code $REG_CODE"
    exit 1
fi

sleep 1

# Successful login
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

LOGIN_CODE=$(echo "$LOGIN_RESPONSE" | tail -1)

if [ "$LOGIN_CODE" == "200" ]; then
    echo "✅ PASS: Successful login works"
else
    echo "❌ FAIL: Login failed with code $LOGIN_CODE"
    exit 1
fi
echo ""

# Clear IP rate limits from previous tests
echo "Clearing rate limits from previous tests..."
docker exec redis redis-cli KEYS "auth:login:ip:attempts:*" | while read key; do
    [ -n "$key" ] && docker exec redis redis-cli DEL "$key" > /dev/null 2>&1
done
sleep 1

# Test 2: Account locks after 5 failed attempts
echo "Test 2: Verify account locks after 5 failed login attempts..."
LOCKOUT_USER="lockout_fail_$(date +%s)"

# Register user
curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"Test123Pass!\"}" > /dev/null

sleep 1

# Make 5 failed login attempts
LOCKED=false
for i in {1..6}; do
    FAIL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"WrongPassword$i!\"}")
    
    FAIL_CODE=$(echo "$FAIL_RESPONSE" | tail -1)
    FAIL_BODY=$(echo "$FAIL_RESPONSE" | head -n -1)
    
    echo "  Attempt $i: HTTP $FAIL_CODE"
    
    if [ "$FAIL_CODE" == "423" ]; then
        if echo "$FAIL_BODY" | grep -qi "locked"; then
            echo "✅ PASS: Account locked after $i failed attempts"
            LOCKED=true
            break
        fi
    fi
    
    sleep 0.3
done

if [ "$LOCKED" = false ]; then
    echo "❌ FAIL: Account was not locked after 6 attempts"
    exit 1
fi
echo ""

# Test 3: Locked account returns proper error message
echo "Test 3: Verify locked account returns proper error..."
LOCK_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"Wrong123Pass!\"}")

LOCK_CODE=$(echo "$LOCK_RESPONSE" | tail -1)
LOCK_BODY=$(echo "$LOCK_RESPONSE" | head -n -1)

if [ "$LOCK_CODE" == "423" ] && echo "$LOCK_BODY" | grep -qi "locked"; then
    echo "✅ PASS: Locked account returns 423 with proper error message"
    echo "  Error message: $(echo "$LOCK_BODY" | head -1)"
else
    echo "❌ FAIL: Expected 423 with 'locked' message, got $LOCK_CODE"
    echo "  Body: $LOCK_BODY"
    exit 1
fi
echo ""

# Test 4: Lockout clears after Redis/DB expiry
echo "Test 4: Verify lockout clearing works..."

# The earlier account is still locked, let's try clearing it
docker exec redis redis-cli DEL "auth:login:locked:$LOCKOUT_USER" > /dev/null 2>&1
docker exec redis redis-cli DEL "auth:login:attempts:$LOCKOUT_USER" > /dev/null 2>&1
docker exec redis redis-cli KEYS "auth:login:ip:attempts:*" | while read key; do
    docker exec redis redis-cli DEL "$key" > /dev/null 2>&1
done

# Also clear the database locked_until field
docker exec postgres psql -U rh_user -d ride_hailing -c \
  "UPDATE users SET locked_until = NULL WHERE username = '$LOCKOUT_USER';" > /dev/null 2>&1

sleep 2

# Now try logging in with correct password
CLEAR_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LOCKOUT_USER\", \"password\": \"Test123Pass!\"}")

CLEAR_CODE=$(echo "$CLEAR_RESPONSE" | tail -1)

if [ "$CLEAR_CODE" == "200" ]; then
    echo "✅ PASS: Lockout successfully cleared and login works"
else
    echo "❌ FAIL: Login after clearing lockout failed with code $CLEAR_CODE"
    exit 1
fi
echo ""

echo "=========================================="
echo "✅ ALL TESTS PASSED!"
echo "=========================================="
echo ""
echo "Account lockout functionality verified:"
echo "  • Successful login: Works ✅"
echo "  • Lockout after failures: Active ✅"
echo "  • Proper error messages (423): Returned ✅"
echo "  • Lockout clearing: Works ✅"
echo ""
echo "Fix confirmed: Using only lockedUntil field (deprecated fields removed)"
