#!/bin/bash
# Quick API Test Script

GATEWAY_URL="http://localhost:8080"
ADMIN_URL="http://localhost:9091"
TEST_USER="user_$(date +%s)"
PASS="TestPass123!"

echo "=== RIDO API TEST ==="
echo ""

# Admin Health
echo -n "Admin Health: "
curl -s -o /dev/null -w "%{http_code}" "$ADMIN_URL/admin/health"
echo ""

# Admin Login
echo -n "Admin Login: "
ADMIN_RESP=$(curl -s -X POST "$ADMIN_URL/admin/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"adminpass"}')
ADMIN_TOKEN=$(echo "$ADMIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$ADMIN_TOKEN" ]; then echo "200 + token"; else echo "FAILED"; fi

# Register
echo -n "Register: "
curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"$PASS\"}"
echo ""

# Login
echo -n "Login: "
LOGIN_RESP=$(curl -s -X POST "$GATEWAY_URL/auth/login" -H "Content-Type: application/json" -H "X-Device-Id: test" -d "{\"username\":\"$TEST_USER\",\"password\":\"$PASS\"}")
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
REFRESH=$(echo "$LOGIN_RESP" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then echo "200 + token"; else echo "FAILED"; fi

# /me
echo -n "GET /auth/me: "
curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/auth/me" -H "Authorization: Bearer $TOKEN"
echo ""

# Sessions
echo -n "GET /auth/sessions: "
curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/auth/sessions" -H "Authorization: Bearer $TOKEN"
echo ""

# Refresh
echo -n "POST /auth/refresh: "
curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/refresh" -H "Content-Type: application/json" -H "X-Device-Id: test" -d "{\"refreshToken\":\"$REFRESH\"}"
echo ""

# JWKS
echo -n "GET /auth/keys/jwks.json: "
curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/auth/keys/jwks.json"
echo ""

# Negative tests
echo ""
echo "=== NEGATIVE TESTS ==="
echo -n "Missing username (expect 400): "
curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/register" -H "Content-Type: application/json" -d '{"password":"test123"}'
echo ""

echo -n "Wrong password (expect 401): "
curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_USER\",\"password\":\"wrong\"}"
echo ""

echo -n "No auth on /me (expect 401): "
curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/auth/me"
echo ""

echo -n "Invalid token (expect 401): "
curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/auth/me" -H "Authorization: Bearer invalid"
echo ""

# Logout
echo -n "Logout: "
curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/auth/logout" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "{\"refreshToken\":\"$REFRESH\"}"
echo ""

echo ""
echo "=== DONE ==="
