#!/bin/bash
# Debug Session Limit Test

GATEWAY_URL="http://localhost:8080"
echo "=== Debug Session Limit Test ==="

# Register user
USER="testdebug$(date +%s)"
echo "Registering user: $USER"
REGISTER=$(curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Pass123!\"}")

echo "Register response: $REGISTER"

# Login 6 times
echo "Creating 6 sessions..."
for i in {1..6}; do
    echo "  Session $i (dev$i)"
    RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$USER\",\"password\":\"Pass123!\",\"deviceId\":\"dev$i\"}")
    
    echo "  Login response: $RESPONSE"
    
    if [[ "$RESPONSE" == *"accessToken"* ]]; then
        if [ $i -eq 6 ]; then
            TOKEN=$(echo "$RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
            echo "  Captured TOKEN: $TOKEN"
        fi
    else
        echo "  Failed to get token"
    fi
done

# Check session count
echo "Checking active sessions..."
SESSIONS=$(curl -s -X GET "$GATEWAY_URL/auth/sessions" \
  -H "Authorization: Bearer $TOKEN")

echo "Sessions response: $SESSIONS"

COUNT=$(echo "$SESSIONS" | grep -o '"id"' | wc -l)
echo "Active sessions count: $COUNT"
