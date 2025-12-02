#!/bin/bash
echo "=== 11 - Security Attack Tests ==="
BASE_URL="http://localhost:8080"

echo "🛡️ SQL Injection Tests"
SQL_PAYLOADS=(
  "admin' OR '1'='1"
  "'; DROP TABLE users; --"
  "admin'--"
  "1' UNION SELECT NULL--"
)

for payload in "${SQL_PAYLOADS[@]}"; do
  echo "Testing: $payload"
  curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$payload\",\"password\":\"test\"}" | jq -c '{status,error}'
  sleep 0.3
done
echo ""

echo "🛡️ XSS Attack Tests"
XSS_PAYLOADS=(
  "<script>alert('xss')</script>"
  "<img src=x onerror=alert(1)>"
  "javascript:alert(1)"
)

for payload in "${XSS_PAYLOADS[@]}"; do
  echo "Testing: $payload"
  curl -s -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$payload\",\"password\":\"test\"}" | jq -c '{status,error}'
  sleep 0.3
done
echo ""

echo "🛡️ Null Byte Attack"
curl -s -X GET "$BASE_URL/auth/check-username?username=admin%00" | jq
echo ""

echo "✅ Security attack tests complete!"
