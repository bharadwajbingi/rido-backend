#!/bin/bash
source ./common.sh

section "13 - Debug/Actuator Endpoint Exposure Tests"

# Check standard sensitive endpoints
ENDPOINTS=("/actuator" "/actuator/env" "/actuator/heapdump" "/actuator/beans")

for EP in "${ENDPOINTS[@]}"; do
    echo -n "Checking $EP ... "
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$AUTH_URL$EP")
    
    if [ "$CODE" == "200" ]; then
        warn "Endpoint $EP is EXPOSED (200). Ensure this is blocked by Gateway/Firewall!"
    elif [ "$CODE" == "401" ] || [ "$CODE" == "403" ]; then
        pass "Endpoint $EP is Protected ($CODE)"
    elif [ "$CODE" == "404" ]; then
        pass "Endpoint $EP Not Found (Safe)"
    else
        echo "Got $CODE"
    fi
done

section "Debug Endpoint Tests Complete"
