#!/bin/bash
# ============================================================================
# Test Helpers for Profile Service Smoke Tests
# Common utilities and color definitions
# ============================================================================

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Environment variables (with defaults)
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
AUTH_URL="${AUTH_URL:-http://localhost:9091}"  # Standalone mode: admin port

# Helper function to check if jq is available
check_dependencies() {
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}❌ Error: jq is required but not installed${NC}"
        echo "   Install with: apt-get install jq (Ubuntu) or brew install jq (macOS)"
        exit 1
    fi
}

# Call dependency check
check_dependencies

# Helper function to generate unique test username
generate_test_username() {
    local prefix="${1:-test_user}"
    echo "${prefix}_$(date +%s)_$$"
}

# Helper function to register a user and return token and userId
register_user() {
    local username="$1"
    local password="$2"
    local role="${3:-RIDER}"
    
    local response=$(curl -s -X POST "$AUTH_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$username\",\"password\":\"$password\",\"role\":\"$role\"}")
    
    echo "$response"
}

# Helper function to extract token from register response
extract_token() {
    echo "$1" | jq -r '.token // empty'
}

# Helper function to extract userId from register response
extract_user_id() {
    echo "$1" | jq -r '.userId // empty'
}

# Helper function to check HTTP status code
check_http_status() {
    local expected="$1"
    local actual="$2"
    local error_msg="${3:-HTTP status mismatch}"
    
    if [ "$actual" != "$expected" ]; then
        echo -e "${RED}❌ $error_msg: expected $expected, got $actual${NC}"
        return 1
    fi
    return 0
}

# Helper function to validate non-empty value
validate_not_empty() {
    local value="$1"
    local field_name="$2"
    
    if [ -z "$value" ]; then
        echo -e "${RED}❌ $field_name is empty or missing${NC}"
        return 1
    fi
    return 0
}

# Helper to print test step
print_step() {
    echo "→ $1..."
}

# Helper to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Helper to print warning
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Helper to print error
print_error() {
    echo -e "${RED}❌ $1${NC}"
}
