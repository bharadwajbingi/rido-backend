#!/bin/bash
source ./common.sh

section "01 - Endpoint Availability Discovery"

check_endpoint() {
    local method=$1
    local url=$2
    local expected_codes=$3
    local desc=$4

    echo -n "Checking $method $url ... "
    # Capture error output from curl to debug 000 cases
    # We strip -s from this specific call to capture errors if needed, but wrapper adds it.
    # So we trust wrapper but redirect stderr to file
    curl -v -o /dev/null -w "%{http_code}" -X "$method" "$url" 2> /tmp/curl_err > /tmp/curl_out
    
    local code
    code=$(cat /tmp/curl_out)
    
    if [[ "$code" =~ $expected_codes ]]; then
        pass "$desc (Got $code)"
    else
        # If code is 000, print curl error
        local err=""
        if [ "$code" == "000" ]; then
            err=$(cat /tmp/curl_err)
        fi
        fail "$desc" "Expected one of [$expected_codes], got $code. Curl Error: $err"
    fi
}

# Public Endpoints
check_endpoint "GET" "$AUTH_URL/auth/check-username?username=test" "200" "Check Username"
check_endpoint "POST" "$AUTH_URL/auth/register" "400|415|200" "Register (Public)"
check_endpoint "POST" "$AUTH_URL/auth/login" "400|415|200" "Login (Public)"
check_endpoint "POST" "$AUTH_URL/auth/refresh" "400|415|200" "Refresh (Public)"

# Protected Endpoints 
check_endpoint "POST" "$AUTH_URL/auth/logout" "401|400|403|200" "Logout"
check_endpoint "GET" "$AUTH_URL/auth/me" "401|403" "Verify Me (Unauthorized)"
check_endpoint "GET" "$AUTH_URL/auth/sessions" "401|403" "Verify Sessions (Unauthorized)"

# Admin Endpoints (Port 9091)
check_endpoint "GET" "$ADMIN_URL/admin/health" "200|404|403" "Admin Health"
check_endpoint "POST" "$ADMIN_URL/admin/login" "400|415|200|401" "Admin Login"

section "Endpoint Discovery Complete"
