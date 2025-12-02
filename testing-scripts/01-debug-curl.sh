#!/bin/bash

AUTH="http://localhost:8081/auth"
SECURE="http://localhost:8080/secure/info"

USER="ctx_$RANDOM"
PASS="pass123"

echo "Registering..."
curl -s -X POST "$AUTH/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}"

echo -e "\nLogging in..."
LOGIN=$(curl -s -X POST "$AUTH/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")

ACCESS=$(echo "$LOGIN" | jq -r '.accessToken')
echo "Token: $ACCESS"

echo -e "\nCalling Secure Endpoint..."
curl -v -X GET "$SECURE" \
  -H "Authorization: Bearer $ACCESS"
