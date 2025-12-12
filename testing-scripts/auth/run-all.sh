#!/bin/bash
# Master Test Runner

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Starting Auth Service Comprehensive Test Suite...${NC}"
START_TIME=$(date +%s)

# Helper to run script
run_script() {
    local script=$1
    echo -e "\n${GREEN}>>> Running $script${NC}"
    chmod +x "$script"
    ./"$script"
    if [ $? -ne 0 ]; then
        echo -e "${RED}>>> $script FAILED${NC}"
        # We can choose to exit or continue. Let's continue to see full report.
        FAILURES+=("$script")
    else
        echo -e "${GREEN}>>> $script PASSED${NC}"
    fi
}

FAILURES=()

# List of scripts to run (Sorted)
SCRIPTS=(
    "01-endpoint-discovery.sh"
    "02-functional-tests.sh"
    "03-input-validation.sh"
    "04-rate-limiting.sh"
    "05-lockout.sh"
    "06-session-limit.sh"
    "07-refresh-flow.sh"
    # "08-timing-attack.sh" # Optional/Heuristic
    "09-jwt-forgery-tests.sh"
    # "10-redis-outage-tests.sh" # Manual
    "13-debug-endpoint-tests.sh"
    "14-admin-port-tests.sh"
    "15-security-headers-tests.sh"
    "16-mtls-tests.sh"
    "17-json-robustness-tests.sh"
    "18-data-integrity-tests.sh"
    "19-cross-service-behavior-tests.sh"
)

for SCRIPT in "${SCRIPTS[@]}"; do
    run_script "$SCRIPT"
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Full Suite Completed in ${DURATION}s${NC}"
echo -e "${GREEN}========================================${NC}"

if [ ${#FAILURES[@]} -eq 0 ]; then
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
else
    echo -e "${RED}SOME TESTS FAILED:${NC}"
    for F in "${FAILURES[@]}"; do
        echo -e " - $F"
    done
    exit 1
fi
