#!/bin/bash

# ==============================================================================
# SMOKE TEST 07: INPUT VALIDATION & SANITIZATION
# ==============================================================================
# Verifies:
# 1. Strict regex validation for username/password
# 2. XSS/SQLi sanitization
# 3. Request size limits
# ==============================================================================

BASE_URL="http://localhost:8080/auth"
CONTENT_TYPE="Content-Type: application/json"

echo "----------------------------------------------------------------"
echo "STARTING SMOKE TEST 07: INPUT VALIDATION & SANITIZATION"
echo "----------------------------------------------------------------"

# 1. TEST INVALID USERNAME (Special Chars)
echo "[TEST 1] Register with invalid username (special chars)..."
RESPONSE=$(curl -v -s -X POST "$BASE_URL/register" \
  -H "$CONTENT_TYPE" \
  -d '{
    "username": "user@name!",
    "password": "Password1!"
  }' 2>&1)

echo "RAW RESPONSE: $RESPONSE"

if echo "$RESPONSE" | grep -q "Username can only contain letters"; then
  echo "✅ PASS: Invalid username rejected"
else
  echo "❌ FAIL: Invalid username accepted or wrong error"
  exit 1
fi

# 2. TEST WEAK PASSWORD
echo "[TEST 2] Register with weak password..."
RESPONSE=$(curl -s -X POST "$BASE_URL/register" \
  -H "$CONTENT_TYPE" \
  -d '{
    "username": "weakuser",
    "password": "password"
  }')

if echo "$RESPONSE" | grep -q "Password must contain"; then
  echo "✅ PASS: Weak password rejected"
else
  echo "❌ FAIL: Weak password accepted or wrong error"
  echo "Response: $RESPONSE"
  exit 1
fi

# 3. TEST XSS PAYLOAD IN USERNAME
echo "[TEST 3] Register with XSS payload in username..."
# Note: The filter should strip the tags, but the regex should also reject it.
# We expect rejection either by regex or sanitization resulting in invalid format.
RESPONSE=$(curl -s -X POST "$BASE_URL/register" \
  -H "$CONTENT_TYPE" \
  -d '{
    "username": "<script>alert(1)</script>",
    "password": "Password1!"
  }')

if echo "$RESPONSE" | grep -q "Username can only contain letters" || echo "$RESPONSE" | grep -q "Validation failed"; then
  echo "✅ PASS: XSS payload rejected"
else
  echo "❌ FAIL: XSS payload accepted"
  echo "Response: $RESPONSE"
  exit 1
fi

# 4. TEST SQL INJECTION PAYLOAD
echo "[TEST 4] Register with SQLi payload in username..."
RESPONSE=$(curl -s -X POST "$BASE_URL/register" \
  -H "$CONTENT_TYPE" \
  -d '{
    "username": "admin\" --",
    "password": "Password1!"
  }')

if echo "$RESPONSE" | grep -q "Username can only contain letters" || echo "$RESPONSE" | grep -q "Validation failed"; then
  echo "✅ PASS: SQLi payload rejected"
else
  echo "❌ FAIL: SQLi payload accepted"
  echo "Response: $RESPONSE"
  exit 1
fi

# 5. TEST OVERSIZED REQUEST
echo "[TEST 5] Sending oversized request (>10MB)..."
# Create a large file
dd if=/dev/zero of=large_payload.txt bs=1M count=11 2>/dev/null

# We use curl to send the file as raw data to simulate a large body
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/register" \
  -H "$CONTENT_TYPE" \
  --data-binary @large_payload.txt)

rm large_payload.txt

if [ "$RESPONSE" -eq 413 ]; then
  echo "✅ PASS: Oversized request rejected (413 Payload Too Large)"
else
  echo "❌ FAIL: Oversized request not rejected with 413. Code: $RESPONSE"
  exit 1
fi

echo "----------------------------------------------------------------"
echo "✅ ALL INPUT VALIDATION TESTS PASSED"
echo "----------------------------------------------------------------"
