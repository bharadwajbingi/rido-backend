#!/bin/bash
# Session Limit Test - Via Gateway (Port 8080)

set -e

GATEWAY_URL="http://localhost:8080"
echo "=== Session Limit Test (via Gateway) ==="
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

# Register user
USER="time_$RANDOM"
echo ""
echo "Registering user: $USER"
REGISTER=$(curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Pass123!\"}")

if echo "$REGISTER" | grep -q "error"; then
    echo "❌ Registration failed: $REGISTER"
    exit 1
fi
echo "✅ Registered"

# Login 6 times
echo ""
echo "Creating 6 sessions..."
for i in {1..6}; do
    echo -n "  Session $i (dev$i): "
    RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$USER\",\"password\":\"Pass123!\",\"deviceId\":\"dev$i\"}")
    
    if echo "$RESPONSE" | grep -q "accessToken"; then
        if [ $i -eq 6 ]; then
            TOKEN=$(echo "$RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
        fi
        echo "✅"
    else
        echo "❌ Failed: $RESPONSE"
        exit 1
    fi
done

# Check session count
echo ""
echo "Checking active sessions..."
SESSIONS=$(curl -s -X GET "$GATEWAY_URL/auth/sessions" \
  -H "Authorization: Bearer $TOKEN")

COUNT=$(echo "$SESSIONS" | grep -o '"id"' | wc -l)
echo "  Active sessions: $COUNT"

if [ "$COUNT" -eq 5 ]; then
    echo "✅ PASS: Exactly 5 sessions active (limit enforced!)"
else
    echo "❌ FAIL: Expected 5 sessions, found $COUNT"
    echo "Sessions: $SESSIONS"
    exit 1
fi

# Check logs
echo ""
echo "Checking auth logs for revocation events..."
if docker logs auth 2>&1 | tail -100 | grep -q "exceeded session limit"; then
    echo "✅ PASS: Found 'exceeded session limit' in logs"
    docker logs auth 2>&1 | tail -100 | grep "session limit" | head -3
else
    echo "⚠️  Session limit log not found"
fi

echo ""
echo "==================================="
echo "✅ ALL TESTS PASSED!"
echo "==================================="
echo "Session limit enforcement working correctly!"
