#!/bin/bash
# ============================================================================
# Test: Address CRUD
# Validates address management: create, list, delete
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Address CRUD"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Step 1: Register a rider user
echo "→ Registering rider user..."
USERNAME="test_rider_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"RIDER\"}")

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token // empty')
USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.userId // empty')

if [ -z "$TOKEN" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}❌ Failed to register rider${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Rider registered${NC}"

# Step 2: Add "Home" address
echo "→ Adding Home address..."
HOME_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/rider/addresses" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"label":"Home","lat":12.9716,"lng":77.5946}')

HOME_ID=$(echo "$HOME_RESPONSE" | jq -r '.id // empty')
HOME_LABEL=$(echo "$HOME_RESPONSE" | jq -r '.label // empty')

if [ -z "$HOME_ID" ] || [ "$HOME_LABEL" != "Home" ]; then
    echo -e "${RED}❌ Failed to add Home address${NC}"
    echo "Response: $HOME_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓ Home address added: $HOME_ID${NC}"

# Step 3: Add "Work" address
echo "→ Adding Work address..."
WORK_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/rider/addresses" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"label":"Work","lat":12.9352,"lng":77.6245}')

WORK_ID=$(echo "$WORK_RESPONSE" | jq -r '.id // empty')
WORK_LABEL=$(echo "$WORK_RESPONSE" | jq -r '.label // empty')

if [ -z "$WORK_ID" ] || [ "$WORK_LABEL" != "Work" ]; then
    echo -e "${RED}❌ Failed to add Work address${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Work address added: $WORK_ID${NC}"

# Step 4: List all addresses (should be 2)
echo "→ Listing all addresses..."
LIST_RESPONSE=$(curl -s -X GET "$GATEWAY_URL/profile/rider/addresses" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

ADDRESS_COUNT=$(echo "$LIST_RESPONSE" | jq '. | length')

if [ "$ADDRESS_COUNT" != "2" ]; then
    echo -e "${RED}❌ Expected 2 addresses, got $ADDRESS_COUNT${NC}"
    echo "Response: $LIST_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓ Found 2 addresses${NC}"

# Step 5: Delete Home address
echo "→ Deleting Home address..."
DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$GATEWAY_URL/profile/rider/addresses/$HOME_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

HTTP_CODE=$(echo "$DELETE_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" != "204" ]; then
    echo -e "${RED}❌ Failed to delete address, HTTP code: $HTTP_CODE${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Home address deleted${NC}"

# Step 6: Verify only 1 address remains
echo "→ Verifying deletion..."
FINAL_LIST=$(curl -s -X GET "$GATEWAY_URL/profile/rider/addresses" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

FINAL_COUNT=$(echo "$FINAL_LIST" | jq '. | length')
REMAINING_LABEL=$(echo "$FINAL_LIST" | jq -r '.[0].label // empty')

if [ "$FINAL_COUNT" != "1" ]; then
    echo -e "${RED}❌ Expected 1 address after deletion, got $FINAL_COUNT${NC}"
    exit 1
fi

if [ "$REMAINING_LABEL" != "Work" ]; then
    echo -e "${RED}❌ Wrong address remains: $REMAINING_LABEL${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Only Work address remains${NC}"

# Step 7: Test deleting non-existent address
echo "→ Testing delete of non-existent address..."
FAKE_ID="00000000-0000-0000-0000-000000000000"
DELETE_ERROR=$(curl -s -w "\n%{http_code}" -X DELETE "$GATEWAY_URL/profile/rider/addresses/$FAKE_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

ERROR_HTTP_CODE=$(echo "$DELETE_ERROR" | tail -n1)
if [ "$ERROR_HTTP_CODE" = "204" ]; then
    echo -e "${YELLOW}⚠ Warning: Deleting non-existent address returned 204 (should error)${NC}"
else
    echo -e "${GREEN}✓ Correctly handled non-existent address deletion${NC}"
fi

echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
exit 0
