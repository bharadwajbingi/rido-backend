#!/bin/bash
# Smoke Test: Account Lockout (Standalone Mode)
# Verifies that accounts get locked after too many failed login attempts

set -e

# Direct Auth service port (standalone mode - no Gateway)
AUTH_URL="${AUTH_URL:-http://localhost:8081}"

echo "=========================================="
echo "Account Lockout Smoke Test (Standalone)"
echo "=========================================="
echo ""

# Wait for Auth service readiness
# Readiness check skipped

# Clear rate limits and lockouts
echo "Clearing rate limits and lockouts..."
docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true
sleep 1
echo ""

# Create a test user
USERNAME="lockout_$(date +%s)"
PASSWORD="SecurePass123!"

echo "Step 1: Registering test user: $USERNAME"
REGISTER=$(curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

if echo "$REGISTER" | grep -q "error"; then
    echo "❌ Failed to register: $REGISTER"
    exit 1
fi
echo "✅ User registered"
echo ""

# ==============================================
# TEST 1: Fail login multiple times to trigger lockout
# ==============================================
echo "Test 1: Triggering account lockout..."
echo "  (Attempting 6 failed logins - lockout triggers at 5)"

MAX_ATTEMPTS=6  # Default lockout is 5 attempts

for i in $(seq 1 $MAX_ATTEMPTS); do
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
      -H "Content-Type: application/json" \
      -H "User-Agent: LockoutTest/1.0" \
      -d "{\"username\": \"$USERNAME\", \"password\": \"WrongPass$i!\"}")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    
    if [ "$HTTP_CODE" == "423" ]; then
        echo "  Attempt $i: Account LOCKED (423)"
        LOCKED_AT=$i
        break
    elif [ "$HTTP_CODE" == "429" ]; then
        echo "  Attempt $i: Rate limited (429) - waiting..."
        sleep 2
    else
        echo "  Attempt $i: Invalid credentials (HTTP $HTTP_CODE)"
    fi
    
    sleep 0.5
done
echo ""

# ==============================================
# TEST 2: Verify locked account returns 423
# ==============================================
echo "Test 2: Verifying locked account returns 423..."
LOCKED_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: LockoutTest/1.0" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

LOCKED_CODE=$(echo "$LOCKED_RESPONSE" | tail -1)
LOCKED_BODY=$(echo "$LOCKED_RESPONSE" | head -n -1)

echo "  HTTP Status: $LOCKED_CODE"

if [ "$LOCKED_CODE" == "423" ]; then
    echo "✅ PASS: Locked account correctly returns 423"
else
    echo "❌ FAIL: Expected 423, got $LOCKED_CODE"
    echo "  Response: $LOCKED_BODY"
    exit 1
fi
echo ""

# ==============================================
# TEST 3: Verify correct user login still works (different user)
# ==============================================
echo "Test 3: Verify different user can still login..."
OTHER_USER="other_$(date +%s)"

# Register other user
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$OTHER_USER\", \"password\": \"$PASSWORD\"}" > /dev/null

# Login other user
OTHER_LOGIN=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: LockoutTest/1.0" \
  -d "{\"username\": \"$OTHER_USER\", \"password\": \"$PASSWORD\"}")

OTHER_CODE=$(echo "$OTHER_LOGIN" | tail -1)

if [ "$OTHER_CODE" == "200" ]; then
    echo "✅ PASS: Other users can still login (200)"
else
    echo "❌ FAIL: Other user login failed with $OTHER_CODE"
    exit 1
fi
echo ""

echo "=========================================="
echo "✅ ACCOUNT LOCKOUT VERIFIED"
echo "=========================================="
echo ""
echo "Security features working:"
echo "  • Failed login attempts tracked ✅"
echo "  • Account locked after max attempts ✅"
echo "  • Locked account returns 423 ✅"
echo "  • Other accounts unaffected ✅"
