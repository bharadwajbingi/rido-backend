#!/bin/bash
# Run All Smoke Tests
# Executes all smoke tests in sequence and reports results

echo "=========================================="
echo "Running All Smoke Tests"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to run a test
run_test() {
    local test_file=$1
    local test_name=$(basename "$test_file" .sh)
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo "----------------------------------------"
    echo "Running: $test_name"
    echo "----------------------------------------"
    
    if bash "$test_file"; then
        echo -e "${GREEN}✅ PASSED${NC}: $test_name"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAILED${NC}: $test_name"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo ""
}

# Run all smoke tests in order
run_test "$SCRIPT_DIR/01-session-limit-enforcement.sh"
run_test "$SCRIPT_DIR/02-timing-attack-mitigation.sh"
run_test "$SCRIPT_DIR/03-basic-auth-flow.sh"
run_test "$SCRIPT_DIR/04-debug-controller-removed.sh"
run_test "$SCRIPT_DIR/05-session-cleanup-batching.sh"
run_test "$SCRIPT_DIR/06-rate-limit-bypass-prevention.sh"
run_test "$SCRIPT_DIR/07-input-validation.sh"

# Summary
echo "=========================================="
echo "Smoke Test Summary"
echo "=========================================="
echo "Total tests:  $TOTAL_TESTS"
echo -e "Passed:       ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed:       ${RED}$FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✅ ALL SMOKE TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}❌ SOME TESTS FAILED${NC}"
    exit 1
fi
