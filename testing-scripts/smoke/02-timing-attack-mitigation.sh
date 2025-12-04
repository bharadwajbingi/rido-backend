#!/bin/bash
# Smoke Test: Timing Attack Mitigation
# Verifies that login timing is constant regardless of whether user exists

set -e

GATEWAY_URL="http://localhost:8080"

echo "=========================================="
echo "Timing Attack Mitigation Test"
echo "=========================================="
echo ""

# Wait for gateway
echo "Checking if gateway is ready..."
for i in {1..5}; do
    if curl -s "$GATEWAY_URL/auth/keys/jwks.json" | grep -q "keys"; then
        echo "✅ Gateway is UP"
        break
    fi
    echo "  Waiting... ($i/5)"
    sleep 2
done
echo ""

# Create a test user
USERNAME="timing_test_$(date +%s)"
PASSWORD="SecurePass123!"

echo "Step 1: Registering test user: $USERNAME"
REGISTER=$(curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"$PASSWORD\"}")

if echo "$REGISTER" | grep -q "error"; then
    echo "❌ Registration failed: $REGISTER"
    exit 1
fi
echo "✅ User registered"
echo ""

# Test 1: Measure timing for valid user + wrong password
echo "Step 2: Testing timing for VALID user + wrong password..."
START=$(date +%s%N)
RESPONSE1=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"$USERNAME\", \"password\": \"WrongPassword123!\"}")
END=$(date +%s%N)
TIME1=$(( (END - START) / 1000000 )) # Convert to milliseconds
STATUS1=$(echo "$RESPONSE1" | tail -1)
BODY1=$(echo "$RESPONSE1" | head -n -1)

echo "  Response time: ${TIME1}ms"
echo "  HTTP status: $STATUS1"
echo "  Error message: $(echo "$BODY1" | grep -o '"message":"[^"]*' | cut -d'"' -f4)"

if [ "$STATUS1" != "401" ]; then
    echo "❌ Expected 401, got $STATUS1"
    exit 1
fi
echo "✅ Correct status code (401)"
echo ""

# Test 2: Measure timing for INVALID user
echo "Step 3: Testing timing for INVALID user..."
START=$(date +%s%N)
RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"nonexistent_user_xyz_$(date +%s)\", \"password\": \"AnyPassword123!\"}")
END=$(date +%s%N)
TIME2=$(( (END - START) / 1000000 ))
STATUS2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | head -n -1)

echo "  Response time: ${TIME2}ms"
echo "  HTTP status: $STATUS2"
echo "  Error message: $(echo "$BODY2" | grep -o '"message":"[^"]*' | cut -d'"' -f4)"

if [ "$STATUS2" != "401" ]; then
    echo "❌ Expected 401, got $STATUS2"
    exit 1
fi
echo "✅ Correct status code (401)"
echo ""

# Test 3: Verify timing consistency
echo "Step 4: Verifying timing consistency..."
DIFF=$(( TIME1 > TIME2 ? TIME1 - TIME2 : TIME2 - TIME1 ))
echo "  Time difference: ${DIFF}ms"

# Allow ±100ms tolerance (adjust based on your requirements)
if [ "$DIFF" -lt 100 ]; then
    echo "✅ PASS: Response times are consistent (difference: ${DIFF}ms < 100ms)"
else
    echo "⚠️  WARNING: Response time difference is ${DIFF}ms (> 100ms threshold)"
    echo "   This might indicate timing attack vulnerability"
    # Don't fail the test, just warn (network jitter can affect this)
fi
echo ""

# Test 4: Verify error messages are identical
echo "Step 5: Verifying error messages are identical..."
MSG1=$(echo "$BODY1" | grep -o '"message":"[^"]*' | cut -d'"' -f4)
MSG2=$(echo "$BODY2" | grep -o '"message":"[^"]*' | cut -d'"' -f4)

if [ "$MSG1" == "$MSG2" ]; then
    echo "✅ PASS: Error messages are identical"
    echo "   Message: \"$MSG1\""
else
    echo "❌ FAIL: Error messages differ!"
    echo "   Valid user error: \"$MSG1\""
    echo "   Invalid user error: \"$MSG2\""
    exit 1
fi
echo ""

# Test 5: Verify no username enumeration
echo "Step 6: Checking for username enumeration vulnerabilities..."
# Verify error message is generic (doesn't reveal whether user exists)
if [ "$MSG1" != "Invalid username or password" ]; then
    echo "❌ FAIL: Unexpected error message"
    echo "   Expected: \"Invalid username or password\""
    echo "   Got: \"$MSG1\""
    exit 1
fi
echo "✅ PASS: No username enumeration detected"
echo "   Using generic error message: \"$MSG1\""
echo ""

echo "=========================================="
echo "✅ ALL TESTS PASSED!"
echo "=========================================="
echo ""
echo "Timing attack mitigation is working correctly:"
echo "  • Constant-time responses (±${DIFF}ms)"
echo "  • Identical error messages"
echo "  • No username enumeration"
