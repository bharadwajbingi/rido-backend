#!/bin/bash
# Debug Rate Limit (mTLS)
CERT_DIR="../../infra/mtls-certs"
CRT="$CERT_DIR/gateway/gateway.crt"
KEY="$CERT_DIR/gateway/gateway.key"
URL="https://localhost:8081"

curl() { command curl -k --cert "$CRT" --key "$KEY" "$@"; }

UUID=$(date +%s)
LOGIN_PAYLOAD="{\"username\": \"ratetest${UUID}\", \"password\": \"Password123!\"}"

# Register
echo "Registering..."
curl -s -X POST "$URL/auth/register" -H "Content-Type: application/json" -d "$LOGIN_PAYLOAD"
echo ""

echo "Attempting 60 logins..."
count=0
success=0
limited=0

for i in {1..60}; do
  status=$(curl -o /dev/null -s -w "%{http_code}" -X POST "$URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: 203.0.113.88" \
    -d "$LOGIN_PAYLOAD")
  
  if [ "$status" == "200" ]; then
    success=$((success+1))
    echo -n "."
  elif [ "$status" == "429" ]; then
    limited=$((limited+1))
    echo -n "L"
  else
    echo -n "E($status)"
  fi
  # Low sleep
  # sleep 0.05
done

echo ""
echo "Success: $success, Limited: $limited"
if [ $limited -gt 0 ]; then
  echo "PASS: Rate limit triggered."
  exit 0
else
  echo "FAIL: Rate limit NOT triggered."
  exit 1
fi
