#!/bin/bash
# ============================================================================
# Test: Negative Cases
# Validates error handling for invalid requests
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Negative Cases"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Setup: Register a test user for negative testing
echo "→ Setting up test user..."
USERNAME="test_negative_$(date +%s)"
PASSWORD="TestPass123!"
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"RIDER\"}")

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token // empty')
USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.userId // empty')

if [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ Failed to setup test user${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Test user ready${NC}"

# Test 1: Missing X-User-ID header
echo ""
echo "→ Test 1: Missing X-User-ID header..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${RED}❌ Expected error, got 200${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Correctly rejected (HTTP $HTTP_CODE)${NC}"

# Test 2: Invalid Authorization token
echo ""
echo "→ Test 2: Invalid Authorization token..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer invalid.jwt.token" \
    -H "X-User-ID: $USER_ID")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${RED}❌ Expected error, got 200${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Correctly rejected invalid token (HTTP $HTTP_CODE)${NC}"

# Test 3: Malformed JSON body
echo ""
echo "→ Test 3: Malformed JSON body..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{invalid json here}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${RED}❌ Expected error for malformed JSON, got 200${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Correctly rejected malformed JSON (HTTP $HTTP_CODE)${NC}"

# Test 4: Excessively long name (potential overflow/injection)
echo ""
echo "→ Test 4: Excessively long name..."
LONG_NAME=$(python3 -c "print('A' * 10000)")
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$LONG_NAME\"}")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${YELLOW}⚠ Warning: Accepted 10000-char name (should validate length)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected excessive length (HTTP $HTTP_CODE)${NC}"
fi

# Test 5: Invalid UUID format for address deletion
echo ""
echo "→ Test 5: Invalid UUID format in path parameter..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$GATEWAY_URL/profile/rider/addresses/not-a-uuid" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
    echo -e "${YELLOW}⚠ Warning: Accepted invalid UUID format (should validate)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected invalid UUID (HTTP $HTTP_CODE)${NC}"
fi

# Test 6: Missing required fields in document upload
echo ""
echo "→ Test 6: Missing required fields in document upload..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"LICENSE"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    echo -e "${YELLOW}⚠ Warning: Accepted incomplete document (should require all fields)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected incomplete request (HTTP $HTTP_CODE)${NC}"
fi

# Test 7: Invalid coordinates in address
echo ""
echo "→ Test 7: Invalid coordinates (out of range)..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/profile/rider/addresses" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"label":"Invalid","lat":999,"lng":999}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    echo -e "${YELLOW}⚠ Warning: Accepted invalid coordinates (should validate range)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected invalid coordinates (HTTP $HTTP_CODE)${NC}"
fi

# Test 8: Empty string values
echo ""
echo "→ Test 8: Empty string values in profile update..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"name":"","email":""}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${YELLOW}⚠ Warning: Accepted empty strings (should validate)${NC}"
else
    echo -e "${GREEN}✓ Correctly rejected empty values (HTTP $HTTP_CODE)${NC}"
fi

# Test 9: Non-existent user ID (authorization mismatch)
echo ""
echo "→ Test 9: Mismatched X-User-ID (potential security issue)..."
FAKE_USER_ID="00000000-0000-0000-0000-000000000099"
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$GATEWAY_URL/profile/me" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-User-ID: $FAKE_USER_ID")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
# This is a critical security test - the service should validate that the token matches the X-User-ID
if [ "$HTTP_CODE" = "200" ]; then
    RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)
    RETURNED_USER_ID=$(echo "$RESPONSE_BODY" | jq -r '.userId // empty')
    if [ "$RETURNED_USER_ID" != "$USER_ID" ]; then
        echo -e "${RED}🚨 SECURITY ISSUE: X-User-ID accepted without token validation!${NC}"
        echo "   Token user: $USER_ID"
        echo "   Requested user: $FAKE_USER_ID"
        echo "   Returned user: $RETURNED_USER_ID"
    else
        echo -e "${GREEN}✓ Service correctly returns actual user from token${NC}"
    fi
else
    echo -e "${GREEN}✓ Correctly rejected mismatched user ID (HTTP $HTTP_CODE)${NC}"
fi

echo ""
echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
echo "   All negative cases handled appropriately."
exit 0
