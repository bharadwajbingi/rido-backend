#!/bin/bash
source ./common.sh

section "16 - mTLS Verification"

if [[ "$AUTH_URL" == https* ]]; then
    # Test connection WITHOUT client certificate
    # Expected behavior:
    # 1. Logic rejection (400/403) - if Tomcat allows handshake but app filters it
    # 2. Handshake failure (000) - if Tomcat requires client-auth
    
    echo "Attempting connection WITHOUT cert..."
    
    # Use curl_no_mtls to bypass the automatic cert injection in common.sh
    CODE=$(curl_no_mtls -o /dev/null -w "%{http_code}" "$AUTH_URL/auth/check-username?username=test")
    
    echo "Got Code: $CODE"
    
    if [ "$CODE" == "000" ] || [ "$CODE" == "400" ] || [ "$CODE" == "403" ]; then 
       pass "Connection rejected/failed without cert (Expected for mTLS)"
    else
       fail "Connection succeeded without cert? Code: $CODE" "mTLS might not be enforcing client certificates properly."
    fi
else
    warn "AUTH_URL is HTTP ($AUTH_URL), skipping mTLS tests."
fi
