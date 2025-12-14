#!/bin/bash
# ============================================================================
# Test: Get Profile
# Validates GET /profile/me endpoint - auto-creates profile if not exists
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Get Profile"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Step 1: Register a test user
echo "→ Registering test user..."
USERNAME="test_profile_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"RIDER\"}")

TOKEN=$(extract_token "$REGISTER_RESPONSE")
USER_ID=$(extract_user_id "$REGISTER_RESPONSE")

if [ -z "$TOKEN" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}❌ Failed to register user${NC}"
    echo "Response: $REGISTER_RESPONSE"
    echo "DEBUG: TOKEN='$TOKEN'"
    echo "DEBUG: USER_ID='$USER_ID'"
    echo ""
    echo "Attempting to parse response..."
    echo "$REGISTER_RESPONSE" | jq . 2>&1 ||  true
    exit 1
fi
echo -e "${GREEN}✓ User registered: $USER_ID${NC}"

# Step 2: Get profile (should auto-create)
echo "→ Getting profile..."
PROFILE_RESPONSE=$(curl -s -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

PROFILE_USER_ID=$(echo "$PROFILE_RESPONSE" | jq -r '.userId // empty')
PROFILE_NAME=$(echo "$PROFILE_RESPONSE" | jq -r '.name // empty')
PROFILE_ROLE=$(echo "$PROFILE_RESPONSE" | jq -r '.role // empty')

if [ -z "$PROFILE_USER_ID" ]; then
    echo -e "${RED}❌ Failed to get profile${NC}"
    echo "Response: $PROFILE_RESPONSE"
    exit 1
fi

# Step 3: Validate response
if [ "$PROFILE_USER_ID" != "$USER_ID" ]; then
    echo -e "${RED}❌ userId mismatch: expected $USER_ID, got $PROFILE_USER_ID${NC}"
    exit 1
fi

if [ "$PROFILE_ROLE" != "RIDER" ]; then
    echo -e "${RED}❌ role mismatch: expected RIDER, got $PROFILE_ROLE${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Profile retrieved successfully${NC}"
echo "  User ID: $PROFILE_USER_ID"
echo "  Name: $PROFILE_NAME"
echo "  Role: $PROFILE_ROLE"

# Step 4: Test negative case - missing X-User-ID header
echo "→ Testing missing X-User-ID header..."
ERROR_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN")

HTTP_CODE=$(echo "$ERROR_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${RED}❌ Expected error for missing X-User-ID, got 200${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Correctly rejected request without X-User-ID${NC}"

echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
exit 0
