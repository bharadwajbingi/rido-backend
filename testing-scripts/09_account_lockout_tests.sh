#!/bin/bash
echo "=== 09 - Account Lockout Tests ==="
BASE_URL="http://localhost:8080"
TEST_USER="locktest_$(uuidgen | cut -d'-' -f1)"

echo "📋 Setup: Register user"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "Failed login attempts (5 wrong passwords)..."
for i in {1..5}; do
  echo "Attempt $i:"
  curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USER\",\"password\":\"WrongPassword\"}" | jq -c '{status,error}'
  sleep 0.5
done
echo ""

echo "🔒 Test: Login after 5 failed attempts (should be locked)"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "✅ Account lockout tests complete!"
