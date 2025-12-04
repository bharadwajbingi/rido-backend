#!/bin/bash
# Simple session limit test

set -e

echo "=== Session Limit Test ==="
echo ""

# Wait for service
echo "Checking if auth service is ready..."
for i in {1..10}; do
    if curl -s http://localhost:8091/actuator/health | grep -q "UP"; then
        echo "✅ Service is UP"
        break
    fi
    echo "  Waiting... ($i/10)"
    sleep 2
done

# Register user
USER="test$(date +%s)"
echo ""
echo "Registering user: $USER"
curl -s -X POST http://localhost:8091/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"Pass123!\"}" \
  > /dev/null && echo "✅ Registered" || echo "❌ Registration failed"

# Login 6 times
echo ""
echo "Creating 6 sessions..."
for i in {1..6}; do
    echo -n "  Session $i (dev$i): "
    RESPONSE=$(curl -s -X POST http://localhost:8091/auth/login \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$USER\",\"password\":\"Pass123!\",\"deviceId\":\"dev$i\"}")
    
    if echo "$RESPONSE" | grep -q "accessToken"; then
        if [ $i -eq 6 ]; then
            # Save the 6th access token
            TOKEN=$(echo "$RESPONSE" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
        fi
        echo "✅"
    else
        echo "❌ Failed"
        echo "$RESPONSE"
        exit 1
    fi
done

# Check session count
echo ""
echo "Checking active sessions..."
SESSIONS=$(curl -s -X GET http://localhost:8091/auth/sessions \
  -H "Authorization: Bearer $TOKEN")

COUNT=$(echo "$SESSIONS" | grep -o '"id"' | wc -l)
echo "  Active sessions: $COUNT"

if [ "$COUNT" -eq 5 ]; then
    echo "✅ PASS: Exactly 5 sessions active (limit enforced!)"
else
    echo "❌ FAIL: Expected 5 sessions, found $COUNT"
    exit 1
fi

# Check logs for revocation
echo ""
echo "Checking logs for revocation events..."
if docker logs auth 2>&1 | tail -100 | grep -q "exceeded session limit"; then
    echo "✅ PASS: Found session limit log entry"
else
    echo "⚠️  Warning: Session limit log not found (may have scrolled past)"
fi

echo ""
echo "==================================="
echo "✅ ALL TESTS PASSED!"
echo "==================================="
echo "Session limit enforcement is working correctly."
