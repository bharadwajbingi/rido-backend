#!/bin/bash
# ============================================================================
# Test: Update Profile
# Validates PUT /profile/me endpoint - updates name and email
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Update Profile"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Step 1: Register and get initial profile
echo "→ Registering test user..."
USERNAME="test_update_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"RIDER\"}")

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token // empty')
USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.userId // empty')

if [ -z "$TOKEN" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}❌ Failed to register user${NC}"
    exit 1
fi
echo -e "${GREEN}✓ User registered${NC}"

# Get initial profile
INITIAL_PROFILE=$(curl -s -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

INITIAL_NAME=$(echo "$INITIAL_PROFILE" | jq -r '.name // empty')
echo "  Initial name: $INITIAL_NAME"

# Step 2: Update profile
echo "→ Updating profile..."
NEW_NAME="John Doe"
NEW_EMAIL="john.doe@example.com"

UPDATE_RESPONSE=$(curl -s -X PUT "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$NEW_NAME\",\"email\":\"$NEW_EMAIL\"}")

UPDATED_NAME=$(echo "$UPDATE_RESPONSE" | jq -r '.name // empty')
UPDATED_EMAIL=$(echo "$UPDATE_RESPONSE" | jq -r '.email // empty')

if [ "$UPDATED_NAME" != "$NEW_NAME" ]; then
    echo -e "${RED}❌ Name update failed: expected $NEW_NAME, got $UPDATED_NAME${NC}"
    exit 1
fi

if [ "$UPDATED_EMAIL" != "$NEW_EMAIL" ]; then
    echo -e "${RED}❌ Email update failed: expected $NEW_EMAIL, got $UPDATED_EMAIL${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Profile updated successfully${NC}"
echo "  Name: $UPDATED_NAME"
echo "  Email: $UPDATED_EMAIL"

# Step 3: Verify persistence by getting profile again
echo "→ Verifying persistence..."
VERIFIED_PROFILE=$(curl -s -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

VERIFIED_NAME=$(echo "$VERIFIED_PROFILE" | jq -r '.name // empty')
VERIFIED_EMAIL=$(echo "$VERIFIED_PROFILE" | jq -r '.email // empty')

if [ "$VERIFIED_NAME" != "$NEW_NAME" ] || [ "$VERIFIED_EMAIL" != "$NEW_EMAIL" ]; then
    echo -e "${RED}❌ Profile changes not persisted${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Changes persisted correctly${NC}"

# Step 4: Test partial update (only name)
echo "→ Testing partial update..."
PARTIAL_NAME="Jane Smith"
PARTIAL_RESPONSE=$(curl -s -X PUT "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$PARTIAL_NAME\"}")

PARTIAL_UPDATED_NAME=$(echo "$PARTIAL_RESPONSE" | jq -r '.name // empty')
PARTIAL_UPDATED_EMAIL=$(echo "$PARTIAL_RESPONSE" | jq -r '.email // empty')

if [ "$PARTIAL_UPDATED_NAME" != "$PARTIAL_NAME" ]; then
    echo -e "${RED}❌ Partial update failed for name${NC}"
    exit 1
fi

if [ "$PARTIAL_UPDATED_EMAIL" != "$NEW_EMAIL" ]; then
    echo -e "${RED}❌ Email should remain unchanged${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Partial update works correctly${NC}"

echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
exit 0
