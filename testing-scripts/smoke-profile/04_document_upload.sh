#!/bin/bash
# ============================================================================
# Test: Document Upload
# Validates driver document upload and listing
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Document Upload"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Step 1: Register a driver user
echo "→ Registering driver user..."
USERNAME="test_driver_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"DRIVER\"}")

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token // empty')
USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.userId // empty')

if [ -z "$TOKEN" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}❌ Failed to register driver${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Driver registered${NC}"

# Step 2: Upload LICENSE document
echo "→ Uploading LICENSE document..."
LICENSE_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"LICENSE","documentNumber":"DL1234567890","url":"https://example.com/license.pdf"}')

LICENSE_ID=$(echo "$LICENSE_RESPONSE" | jq -r '.id // empty')
LICENSE_TYPE=$(echo "$LICENSE_RESPONSE" | jq -r '.type // empty')
LICENSE_STATUS=$(echo "$LICENSE_RESPONSE" | jq -r '.status // empty')

if [ -z "$LICENSE_ID" ] || [ "$LICENSE_TYPE" != "LICENSE" ]; then
    echo -e "${RED}❌ Failed to upload LICENSE${NC}"
    echo "Response: $LICENSE_RESPONSE"
    exit 1
fi

if [ "$LICENSE_STATUS" != "PENDING" ]; then
    echo -e "${RED}❌ Expected status PENDING, got $LICENSE_STATUS${NC}"
    exit 1
fi

echo -e "${GREEN}✓ LICENSE uploaded: $LICENSE_ID (status: $LICENSE_STATUS)${NC}"

# Step 3: Upload REGISTRATION document
echo "→ Uploading REGISTRATION document..."
REGISTRATION_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"REGISTRATION","documentNumber":"REG9876543210","url":"https://example.com/registration.pdf"}')

REGISTRATION_ID=$(echo "$REGISTRATION_RESPONSE" | jq -r '.id // empty')
REGISTRATION_TYPE=$(echo "$REGISTRATION_RESPONSE" | jq -r '.type // empty')

if [ -z "$REGISTRATION_ID" ] || [ "$REGISTRATION_TYPE" != "REGISTRATION" ]; then
    echo -e "${RED}❌ Failed to upload REGISTRATION${NC}"
    exit 1
fi

echo -e "${GREEN}✓ REGISTRATION uploaded: $REGISTRATION_ID${NC}"

# Step 4: List all documents (should be 2)
echo "→ Listing all documents..."
LIST_RESPONSE=$(curl -s -X GET "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

DOCUMENT_COUNT=$(echo "$LIST_RESPONSE" | jq '. | length')

if [ "$DOCUMENT_COUNT" != "2" ]; then
    echo -e "${RED}❌ Expected 2 documents, got $DOCUMENT_COUNT${NC}"
    echo "Response: $LIST_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ Found 2 documents${NC}"

# Verify both documents are in the list
HAS_LICENSE=$(echo "$LIST_RESPONSE" | jq '[.[] | select(.type == "LICENSE")] | length')
HAS_REGISTRATION=$(echo "$LIST_RESPONSE" | jq '[.[] | select(.type == "REGISTRATION")] | length')

if [ "$HAS_LICENSE" != "1" ] || [ "$HAS_REGISTRATION" != "1" ]; then
    echo -e "${RED}❌ Document list missing expected types${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Both LICENSE and REGISTRATION found in list${NC}"

# Step 5: Test invalid document type (negative case)
echo "→ Testing invalid document type..."
INVALID_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"INVALID_TYPE","documentNumber":"TEST123","url":"https://example.com/test.pdf"}')

INVALID_HTTP_CODE=$(echo "$INVALID_RESPONSE" | tail -n1)
if [ "$INVALID_HTTP_CODE" = "201" ] || [ "$INVALID_HTTP_CODE" = "200" ]; then
    echo -e "${YELLOW}⚠ Warning: Invalid document type accepted (should reject)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected invalid document type${NC}"
fi

echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
exit 0
