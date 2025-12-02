#!/bin/bash

echo "Registering..."
curl -v -X POST "http://127.0.0.1:8081/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"manual_user_2","password":"pass123"}'

echo -e "\n\nLogging in..."
LOGIN=$(curl -v -X POST "http://127.0.0.1:8081/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"manual_user_2","password":"pass123"}')

echo "Login Response: $LOGIN"

ACCESS=$(echo "$LOGIN" | jq -r '.accessToken')
echo "Token: $ACCESS"

echo -e "\nCalling Secure Endpoint..."
curl -v -X GET "http://127.0.0.1:8080/secure/info" \
  -H "Authorization: Bearer $ACCESS"
