#!/bin/bash

U="user_$RANDOM"
P="pass123"

echo "Registering user: $U"

curl -s -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\", \"password\":\"$P\"}" | jq

echo "Logging in..."
LOGIN=$(curl -s -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\", \"password\":\"$P\"}")

echo "$LOGIN" | jq

RT_RAW=$(echo "$LOGIN" | jq -r '.refreshToken')
echo "RAW Refresh Token:"
echo "$RT_RAW"

echo
echo "Local SHA-256:"
LOCAL_HASH=$(echo -n "$RT_RAW" | sha256sum | awk '{print $1}')
echo "$LOCAL_HASH"

echo
echo "DB stored value:"
psql -U postgres -d rido -c \
"SELECT token_hash FROM refresh_tokens ORDER BY created_at DESC LIMIT 1;"
