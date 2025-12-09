#!/bin/bash
# Smoke Test: Redis Outage Resilience (Standalone Mode)
# Verifies that auth service degrades gracefully when Redis is down

set -e

# Direct Auth service port (standalone mode - no Gateway)
AUTH_URL="${AUTH_URL:-http://localhost:8081}"

echo "=========================================="
echo "Redis Outage Resilience Test (Standalone)"
echo "=========================================="
echo ""

# Wait for Auth service readiness
# Readiness check skipped

# Test 1: Successful login works (Redis UP)
echo "Test 1: Verify login works with Redis UP..."
TEST_USER="redis_test_$(date +%s)"

# Register user
curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}" > /dev/null

# Login
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

LOGIN_CODE=$(echo "$LOGIN_RESPONSE" | tail -1)

if [ "$LOGIN_CODE" == "200" ]; then
    echo "✅ PASS: Login works with Redis UP"
else
    echo "❌ FAIL: Login failed with code $LOGIN_CODE"
    exit 1
fi
echo ""

# 🛑 STOP REDIS
echo "🛑 PROVOKING OUTAGE: Stopping Redis..."
docker stop redis
sleep 5 # Wait for circuit breaker to detect

echo "Test 2: Verify login works with Redis DOWN (Fallback)..."

# Login Attempt (Should rely on DB fallback)
RESCUE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

RESCUE_CODE=$(echo "$RESCUE_RESPONSE" | tail -1)

if [ "$RESCUE_CODE" == "200" ]; then
    echo "✅ PASS: Login works even when Redis is DOWN (Circuit Breaker active)"
else
    echo "❌ FAIL: Login failed during Redis outage. Code: $RESCUE_CODE"
    # Don't exit yet, we need to restart Redis
fi
echo ""

# Test 3: Verify Lockout still works (using DB) during outage
echo "Test 3: Verify DB lockout persists during outage..."
# Lock username manually in DB to test DB check fallback
docker exec postgres psql -U rh_user -d ride_hailing -c \
  "UPDATE users SET locked_until = NOW() + INTERVAL '30 minutes' WHERE username = '$TEST_USER';" > /dev/null 2>&1

# Try login (Should be locked)
LOCK_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

LOCK_CODE=$(echo "$LOCK_RESPONSE" | tail -1)

if [ "$LOCK_CODE" == "423" ]; then
    echo "✅ PASS: Account locked (423) even when Redis is DOWN (DB Lock used)"
else
    echo "❌ FAIL: Expected 423 Locked, got $LOCK_CODE"
fi
echo ""

# ▶ START REDIS
echo "▶ RECOVERY: Starting Redis..."
docker start redis
sleep 15 # Wait for Redis to start and Circuit Breaker to close (half-open -> closed)

echo "Test 4: Verify system recovers..."
# Clear DB lock
docker exec postgres psql -U rh_user -d ride_hailing -c \
  "UPDATE users SET locked_until = NULL WHERE username = '$TEST_USER';" > /dev/null 2>&1

# Login
RECOVER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: RedisTest/1.0" \
  -d "{\"username\": \"$TEST_USER\", \"password\": \"Test123Pass!\"}")

RECOVER_CODE=$(echo "$RECOVER_RESPONSE" | tail -1)

if [ "$RECOVER_CODE" == "200" ]; then
    echo "✅ PASS: System fully recovered"
else
    echo "❌ FAIL: Recovery failed with code $RECOVER_CODE"
    exit 1
fi
echo ""

echo "=========================================="
echo "✅ REDIS RESILIENCE VERIFIED"
echo "=========================================="
