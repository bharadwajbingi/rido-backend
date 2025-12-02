#!/bin/bash

echo "=== Testing Refresh Token Hashing ==="
echo ""

# Register user
echo "1) Registering test user..."
curl -s -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"hashtest_user","password":"pass123"}' | jq

echo ""
echo "2) Logging in to get refresh token..."
LOGIN=$(curl -s -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"hashtest_user","password":"pass123"}')

echo "$LOGIN" | jq

# Extract refresh token
RT_RAW=$(echo "$LOGIN" | jq -r '.refreshToken')

echo ""
echo "3) Refresh Token (RAW):"
echo "$RT_RAW"

# Calculate SHA-256 hash
LOCAL_HASH=$(echo -n "$RT_RAW" | sha256sum | awk '{print $1}')

echo ""
echo "4) Local SHA-256 Hash:"
echo "$LOCAL_HASH"

echo ""
echo "5) Query database to see stored hash:"
echo "   Run this in your PostgreSQL session:"
echo ""
echo "   SELECT token_hash FROM refresh_tokens ORDER BY created_at DESC LIMIT 1;"
echo ""
echo "6) Compare: The DB hash should match the local hash above!"
