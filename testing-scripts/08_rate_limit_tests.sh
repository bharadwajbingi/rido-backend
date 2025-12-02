#!/bin/bash
echo "=== 08 - Rate Limit Tests ==="
BASE_URL="http://localhost:8080"
TEST_USER="ratetest_$(uuidgen | cut -d'-' -f1)"

echo "📋 Setup: Register user"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "Rapid login attempts (5 in a row)..."
for i in {1..5}; do
  echo "Attempt $i:"
  curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq -c '{status}'
  sleep 0.2
done
echo ""

echo "❌ Test: 6th attempt (should be rate limited)"
curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"SecurePass123!\"}" | jq
echo ""

echo "✅ Rate limit tests complete!"
