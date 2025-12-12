#!/bin/bash
source ./common.sh

section "15 - Security Headers Tests"

curl -s -I "$AUTH_URL/auth/check-username?username=test" > /tmp/headers_15

check_header() {
    local header=$1
    if grep -i -q "$header" /tmp/headers_15; then
        pass "Header found: $header"
    else
        warn "Header MISSING: $header"
    fi
}

check_header "X-Content-Type-Options: nosniff"
check_header "X-Frame-Options: DENY"
check_header "X-XSS-Protection"
check_header "Cache-Control"
# HSTS only relevant if SSL is terminated here, usually Gateway handles it.
# check_header "Strict-Transport-Security"

section "Security Headers Tests Complete"
