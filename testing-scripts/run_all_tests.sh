#!/bin/bash

echo "========================================"
echo "🧪 Running All Rido Auth Tests"
echo "========================================"
echo ""

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=0
FAIL=0

# Function to run a test script
run_test() {
    local script=$1
    echo "▶️  Running: $script"
    echo "----------------------------------------"
    
    if bash "$BASE_DIR/$script"; then
        ((PASS++))
        echo "✅ PASSED: $script"
    else
        ((FAIL++))
        echo "❌ FAILED: $script"
    fi
    
    echo ""
    sleep 1  # Small delay between tests
}

# Run all test scripts in order
run_test "01_registration_tests.sh"
run_test "02_login_tests.sh"
run_test "03_refresh_token_tests.sh"
run_test "04_logout_tests.sh"
run_test "05_jwks_signature_tests.sh"
run_test "06_access_token_tests.sh"
run_test "07_roles_authorization_tests.sh"
run_test "08_rate_limit_tests.sh"
run_test "09_account_lockout_tests.sh"
run_test "10_session_management_tests.sh"
run_test "11_security_attack_tests.sh"
run_test "12_mtls_internal_auth_tests.sh"

# Summary
echo "========================================"
echo "📊 Test Summary"
echo "========================================"
echo "✅ Passed: $PASS"
echo "❌ Failed: $FAIL"
echo "Total:   12"
echo "========================================"

# Exit with error if any tests failed
if [ $FAIL -gt 0 ]; then
    exit 1
fi

exit 0
