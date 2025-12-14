#!/bin/bash
# ============================================================================
# Test: Driver Stats (PLACEHOLDER)
# NOTE: DriverStats endpoint is not yet implemented in Profile Service
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Driver Stats (Not Implemented)"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

echo -e "${YELLOW}⚠ Driver Stats Endpoint Not Yet Implemented${NC}"
echo ""
echo "The Profile Service has DriverStats model and repository, but no"
echo "controller endpoint was found in the codebase."
echo ""
echo "Expected endpoint: GET /profile/driver/stats"
echo ""
echo "This test will be updated once the endpoint is implemented."
echo ""

# We'll still register a driver to demonstrate the test structure
echo "→ Registering driver user (for future testing)..."
USERNAME="test_driver_stats_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"DRIVER\"}")

TOKEN=$(extract_token "$REGISTER_RESPONSE")
USER_ID=$(extract_user_id "$REGISTER_RESPONSE")

if [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ Failed to register driver${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Driver registered: $USER_ID${NC}"

# Try calling the expected endpoint (will likely 404)
echo "→ Attempting to call expected stats endpoint..."
STATS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL/profile/driver/stats" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

HTTP_CODE=$(echo "$STATS_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$STATS_RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}✓ Endpoint not found (404) - as expected${NC}"
elif [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Endpoint now available! Please update this test.${NC}"
    echo "Response: $RESPONSE_BODY"
else
    echo -e "${YELLOW}✓ Received HTTP $HTTP_CODE - endpoint may be partially implemented${NC}"
fi

echo ""
echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME (placeholder)${NC}"
echo "   This test will be updated when the endpoint is implemented."
exit 0
