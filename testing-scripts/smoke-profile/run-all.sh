#!/bin/bash
# ============================================================================
# Profile Service - Master Smoke Test Runner
# Executes all profile service test scripts in order
# ============================================================================

# Ensure we are in the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Global Configuration
export GATEWAY_URL="http://localhost:8080"
export AUTH_URL="http://localhost:9091"  # Standalone mode: use admin port (HTTP)

echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}       RIDO BACKEND - PROFILE SERVICE SMOKE TESTS       ${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo ""

# Global Readiness Check
echo "Checking Service Readiness..."
MAX_RETRIES=30
READY=false

for i in $(seq 1 $MAX_RETRIES); do
    # Check Gateway health
    if curl -s --connect-timeout 2 --max-time 2 "$GATEWAY_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Gateway is UP ($GATEWAY_URL)${NC}"
        READY=true
        break
    fi
    
    echo -ne "   Waiting for services... ($i/$MAX_RETRIES) \r"
    sleep 2
done

if [ "$READY" = false ]; then
    echo -e "${RED}❌ Services NOT READY after 60 seconds. Aborting tests.${NC}"
    exit 1
fi
echo ""

# Find and Run Tests (01-07)
TEST_SCRIPTS=$(ls | grep -E '^[0-9]+.*\.sh$' | sort)
FAILED_TESTS=0
PASSED_TESTS=0
FAILED_SCRIPTS=""

for script in $TEST_SCRIPTS; do
    echo -e "${BLUE}----------------------------------------------------------------------------${NC}"
    echo -e "${BLUE}Running: $script${NC}"
    echo -e "${BLUE}----------------------------------------------------------------------------${NC}"
    
    if bash "$script"; then
        echo -e "${GREEN}✅ PASSED: $script${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAILED: $script${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_SCRIPTS="${FAILED_SCRIPTS}\n  • ${script}"
    fi
    echo ""
    sleep 1
done

# Summary
echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}       TEST SUMMARY       ${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✅ ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}❌ SOME TESTS FAILED:$FAILED_SCRIPTS${NC}"
    exit 1
fi
