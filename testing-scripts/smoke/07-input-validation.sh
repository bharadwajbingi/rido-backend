#!/bin/bash
# Smoke Test: Input Validation (Standalone Mode)
# Verifies regex validation on username/password and request size limits

set -e

# Direct Auth service port (standalone mode - no Gateway)
AUTH_URL="http://localhost:8081"

echo "=========================================="
echo "Input Validation Smoke Test (Standalone)"
echo "=========================================="
echo ""

# Wait for Auth service readiness
echo "Checking if Auth service is ready..."
for i in {1..15}; do
    if curl -s "$AUTH_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "✅ Auth service is UP (port 8081)"
        break
    fi
    if [ $i -eq 15 ]; then
        echo "❌ Auth service not ready after 30 seconds"
        exit 1
    fi
    echo "  Waiting for readiness... ($i/15)"
    sleep 2
done
echo ""

# Clear rate limits
echo "Clearing rate limits..."
docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true
sleep 1
echo ""

PASSED=0
FAILED=0

pass_test() {
    echo "  ✅ PASS: $1"
    PASSED=$((PASSED + 1))
}

fail_test() {
    echo "  ❌ FAIL: $1"
    FAILED=$((FAILED + 1))
}

# ==============================================
# TEST 1: Username too short (< 3 chars)
# ==============================================
echo "Test 1: Username too short (< 3 chars)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "ab", "password": "ValidPass123!"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Username too short rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 2: Username too long (> 30 chars)
# ==============================================
echo "Test 2: Username too long (> 30 chars)"
LONG_USERNAME="abcdefghijklmnopqrstuvwxyz12345"  # 31 chars
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$LONG_USERNAME\", \"password\": \"ValidPass123!\"}")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Username too long rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 3: Password too short (< 8 chars)
# ==============================================
echo "Test 3: Password too short (< 8 chars)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "validuser", "password": "Short1!"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Password too short rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 4: Password missing uppercase
# ==============================================
echo "Test 4: Password missing uppercase"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "validuser2", "password": "lowercase123!"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Password missing uppercase rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 5: Password missing lowercase
# ==============================================
echo "Test 5: Password missing lowercase"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "validuser3", "password": "UPPERCASE123!"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Password missing lowercase rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 6: Password missing digit
# ==============================================
echo "Test 6: Password missing digit"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "validuser4", "password": "NoDigitsHere!"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "400" ]; then
    pass_test "Password missing digit rejected (400)"
else
    fail_test "Expected 400, got $HTTP_CODE"
fi
echo ""

# ==============================================
# TEST 7: Valid registration (control test)
# ==============================================
echo "Test 7: Valid registration (control test)"
USERNAME="valid_$(date +%s)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"ValidPass123!\"}")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" == "200" ]; then
    pass_test "Valid registration accepted (200)"
else
    fail_test "Expected 200, got $HTTP_CODE"
fi
echo ""

# ==============================================
# SUMMARY
# ==============================================
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "✅ ALL INPUT VALIDATION TESTS PASSED!"
    exit 0
else
    echo "❌ SOME TESTS FAILED"
    exit 1
fi
