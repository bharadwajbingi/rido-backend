#!/bin/bash

# common.sh - Helper library with mTLS support

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# mTLS Configuration
# Resolve robust relative paths to the script location
# (assuming running from testing-scripts/auth/)
CERT_FILE="../../infra/mtls-certs/gateway/gateway.crt"
KEY_FILE="../../infra/mtls-certs/gateway/gateway.key"

# Configuration
export AUTH_URL="https://localhost:8081"
# Admin port verified as HTTP (failed TLS handshake)
export ADMIN_URL="http://localhost:9091" 
export CONTENT_TYPE="Content-Type: application/json"

# Check for Certs
if [ ! -f "$CERT_FILE" ]; then
    echo -e "${YELLOW}[WARN] mTLS Cert not found at $CERT_FILE${NC}"
    echo -e "${YELLOW}       Tests against 8081 might fail if mTLS is enforced.${NC}"
fi

# Wrapper for curl to automatically inject mTLS certs for Auth Service
# and use -k (insecure) for all local HTTPS calls
curl() {
    # Check if target is AUTH_URL (port 8081)
    if [[ "$*" == *":8081"* ]]; then
        command curl -s -k --cert "$CERT_FILE" --key "$KEY_FILE" "$@"
    else
        # For Admin (9091) or others, just -k
        # -k is harmless on HTTP
        command curl -s -k "$@"
    fi
}

# Explicitly no-mTLS curl for testing failures (e.g. 16-mtls-tests)
# We also use -s (silent) by default to match the wrapper behavior
curl_no_mtls() {
    command curl -s -k "$@"
}
export -f curl
export -f curl_no_mtls

# Helper: Print Section Header
section() {
    echo -e "\n${BLUE}================================================================${NC}"
    echo -e "${BLUE}>>> $1${NC}"
    echo -e "${BLUE}================================================================${NC}"
}

# Helper: Pass/Fail
pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    if [ -n "$2" ]; then
        echo -e "${RED}Details:${NC} $2"
    fi
    exit 1
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Helper: Check HTTP Status Code
assert_status() {
    local response_file=$1
    local expected_code=$2
    local description=$3

    local actual_code
    actual_code=$(tail -n1 "$response_file")

    if [[ "$actual_code" =~ $expected_code ]]; then
        pass "$description (Got $actual_code)"
    else
        local body
        body=$(head -n -1 "$response_file")
        fail "$description" "Expected $expected_code, got $actual_code. Body: $body"
    fi
}

# Helper: Json Value Extractor
json_val() {
    local json=$1
    local key=$2
    if command -v jq &> /dev/null; then
        # Use jq if available (handles parsing errors gracefully if mixed output)
        echo "$json" | jq -r ".$key" 2>/dev/null
    else
        echo "$json" | grep -o "\"$key\": *\"[^\"]*\"" | cut -d'"' -f4
    fi
}

check_endpoint() {
    local method=$1
    local url=$2
    local expected_codes=$3 # Regex or list e.g., "200|401|403"
    local desc=$4

    echo -n "Checking $method $url ... "
    local code
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

# Helper: Register User
register_user() {
    local username=$1
    local password=$2
    
    curl -w "\n%{http_code}" -X POST "$AUTH_URL/auth/register" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$username\", \"password\": \"$password\"}" > /tmp/reg_res
        
    local code
    code=$(tail -n1 /tmp/reg_res)
    if [ "$code" != "200" ]; then
        return 1
    fi
    return 0
}

# Helper: Login User and Get Token
login_user() {
    local username=$1
    local password=$2
    
    curl -X POST "$AUTH_URL/auth/login" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$username\", \"password\": \"$password\"}" > /tmp/login_res
        
    local token
    token=$(json_val "$(cat /tmp/login_res)" "accessToken")
    echo "$token"
}

# Helper: Get Admin Token
get_admin_token() {
    local username=${FIRST_ADMIN_USERNAME:-admin}
    local password=${FIRST_ADMIN_PASSWORD:-admin123} # Default or env
    
    curl -X POST "$ADMIN_URL/admin/login" \
        -H "$CONTENT_TYPE" \
        -d "{\"username\": \"$username\", \"password\": \"$password\"}" > /tmp/admin_login_res
        
    local token
    token=$(json_val "$(cat /tmp/admin_login_res)" "accessToken")
    echo "$token" 
}
