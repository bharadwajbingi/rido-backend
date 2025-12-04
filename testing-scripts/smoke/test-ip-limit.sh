#!/bin/bash
# Quick debug script to see IP rate limit response format

for i in {1..22}; do
  RESP=$(curl -s -w "\n%{http_code}" -X POST 'http://localhost:8080/auth/login' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"user_$i\",\"password\":\"wrong\"}")
  
  CODE=$(echo "$RESP" | tail -1)
  BODY=$(echo "$RESP" | head -n -1)
  
  if [ "$CODE" == "403" ]; then
    echo "========== ATTEMPT $i - HTTP 403 =========="
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    echo "==========================================="
    break
  fi
  
  if [ $i -eq 22 ]; then
    echo "All 22 attempts completed without 403"
  fi
  
  sleep 0.1
done
