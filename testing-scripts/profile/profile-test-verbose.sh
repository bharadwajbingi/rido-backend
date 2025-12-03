#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

GATEWAY="http://localhost:8080"
RANDOM_USER="user_$(uuidgen | cut -d'-' -f1)"
PASSWORD="StrongPass123!"
DEVICE_ID="test-device"
USER_AGENT="curl-test"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Profile Service Integration Test (Verbose)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Register User
echo -e "${YELLOW}Step 1: REGISTER USER${NC}"
echo "  → POST $GATEWAY/auth/register"
echo "  → Username: $RANDOM_USER"
echo "  → Password: $PASSWORD"
echo ""

REGISTER_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X POST "$GATEWAY/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"$PASSWORD\"}")

HTTP_STATUS=$(echo "$REGISTER_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
REGISTER_BODY=$(echo "$REGISTER_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body: $REGISTER_BODY"

if [ "$HTTP_STATUS" != "200" ] && [ "$HTTP_STATUS" != "201" ]; then
    echo -e "  ${RED}✗ Registration failed!${NC}"
else
    echo -e "  ${GREEN}✓ Registration successful${NC}"
fi
echo ""

# Step 2: Login User
echo -e "${YELLOW}Step 2: LOGIN USER${NC}"
echo "  → POST $GATEWAY/auth/login"
echo "  → Username: $RANDOM_USER"
echo ""

LOGIN_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X POST "$GATEWAY/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: $DEVICE_ID" \
  -H "User-Agent: $USER_AGENT" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"$PASSWORD\"}")

HTTP_STATUS=$(echo "$LOGIN_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
LOGIN_BODY=$(echo "$LOGIN_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$LOGIN_BODY" | jq '.' 2>/dev/null || echo "$LOGIN_BODY"

ACCESS=$(echo $LOGIN_BODY | jq -r '.accessToken' 2>/dev/null)
REFRESH=$(echo $LOGIN_BODY | jq -r '.refreshToken' 2>/dev/null)
USER_ID=$(echo $LOGIN_BODY | jq -r '.userId' 2>/dev/null)

if [ "$ACCESS" != "null" ] && [ -n "$ACCESS" ]; then
    echo -e "  ${GREEN}✓ Login successful${NC}"
    echo "  → Access Token: ${ACCESS:0:50}..."
    echo "  → Refresh Token: $REFRESH"
    echo "  → User ID: $USER_ID"
else
    echo -e "  ${RED}✗ Login failed - no access token received${NC}"
    exit 1
fi
echo ""

# Step 3: Get Profile
echo -e "${YELLOW}Step 3: GET PROFILE${NC}"
echo "  → GET $GATEWAY/profile/me"
echo "  → Authorization: Bearer <token>"
echo ""

PROFILE_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X GET "$GATEWAY/profile/me" \
  -H "Authorization: Bearer $ACCESS")

HTTP_STATUS=$(echo "$PROFILE_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
PROFILE_BODY=$(echo "$PROFILE_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$PROFILE_BODY" | jq '.' 2>/dev/null || echo "$PROFILE_BODY"

if [ "$HTTP_STATUS" == "200" ]; then
    echo -e "  ${GREEN}✓ Profile retrieved successfully${NC}"
    
    PROFILE_USER_ID=$(echo "$PROFILE_BODY" | jq -r '.userId' 2>/dev/null)
    PROFILE_NAME=$(echo "$PROFILE_BODY" | jq -r '.name' 2>/dev/null)
    PROFILE_ROLE=$(echo "$PROFILE_BODY" | jq -r '.role' 2>/dev/null)
    
    echo "  → User ID: $PROFILE_USER_ID"
    echo "  → Name: $PROFILE_NAME"
    echo "  → Role: $PROFILE_ROLE"
else
    echo -e "  ${RED}✗ Failed to get profile${NC}"
fi
echo ""

# Step 4: Update Profile
echo -e "${YELLOW}Step 4: UPDATE PROFILE${NC}"
echo "  → PUT $GATEWAY/profile/me"
echo "  → Data: {name: 'Bharadwaj Updated', email: 'bhara@example.com'}"
echo ""

UPDATE_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X PUT "$GATEWAY/profile/me" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"name":"Bharadwaj Updated","email":"bhara@example.com"}')

HTTP_STATUS=$(echo "$UPDATE_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
UPDATE_BODY=$(echo "$UPDATE_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$UPDATE_BODY" | jq '.' 2>/dev/null || echo "$UPDATE_BODY"

if [ "$HTTP_STATUS" == "200" ]; then
    echo -e "  ${GREEN}✓ Profile updated successfully${NC}"
else
    echo -e "  ${RED}✗ Failed to update profile${NC}"
fi
echo ""

# Step 5: Upload Profile Photo
echo -e "${YELLOW}Step 5: UPLOAD PROFILE PHOTO${NC}"
echo "  → POST $GATEWAY/profile/me/photo"
echo ""

PHOTO_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X POST "$GATEWAY/profile/me/photo" \
  -H "Authorization: Bearer $ACCESS")

HTTP_STATUS=$(echo "$PHOTO_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
PHOTO_BODY=$(echo "$PHOTO_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$PHOTO_BODY" | jq '.' 2>/dev/null || echo "$PHOTO_BODY"

if [ "$HTTP_STATUS" == "200" ]; then
    echo -e "  ${GREEN}✓ Photo upload URL generated${NC}"
else
    echo -e "  ${YELLOW}⚠ Photo upload may not be implemented${NC}"
fi
echo ""

# Step 6: Add Rider Address
echo -e "${YELLOW}Step 6: ADD RIDER ADDRESS${NC}"
echo "  → POST $GATEWAY/profile/rider/addresses"
echo "  → Data: {label: 'Home', lat: 12.9716, lng: 77.5946}"
echo ""

ADDRESS_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X POST "$GATEWAY/profile/rider/addresses" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"label":"Home","lat":12.9716,"lng":77.5946}')

HTTP_STATUS=$(echo "$ADDRESS_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
ADDRESS_BODY=$(echo "$ADDRESS_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$ADDRESS_BODY" | jq '.' 2>/dev/null || echo "$ADDRESS_BODY"

if [ "$HTTP_STATUS" == "200" ] || [ "$HTTP_STATUS" == "201" ]; then
    echo -e "  ${GREEN}✓ Address added successfully${NC}"
else
    echo -e "  ${RED}✗ Failed to add address${NC}"
fi
echo ""

# Step 7: Get Rider Addresses
echo -e "${YELLOW}Step 7: GET RIDER ADDRESSES${NC}"
echo "  → GET $GATEWAY/profile/rider/addresses"
echo ""

ADDRESSES_RESP=$(curl -w "\nHTTP_STATUS:%{http_code}" -s -X GET "$GATEWAY/profile/rider/addresses" \
  -H "Authorization: Bearer $ACCESS")

HTTP_STATUS=$(echo "$ADDRESSES_RESP" | grep "HTTP_STATUS" | cut -d':' -f2)
ADDRESSES_BODY=$(echo "$ADDRESSES_RESP" | sed '/HTTP_STATUS/d')

echo -e "  ${GREEN}Response Status: $HTTP_STATUS${NC}"
echo "  Response Body:"
echo "$ADDRESSES_BODY" | jq '.' 2>/dev/null || echo "$ADDRESSES_BODY"

if [ "$HTTP_STATUS" == "200" ]; then
    ADDRESS_COUNT=$(echo "$ADDRESSES_BODY" | jq 'length' 2>/dev/null)
    echo -e "  ${GREEN}✓ Retrieved $ADDRESS_COUNT address(es)${NC}"
else
    echo -e "  ${RED}✗ Failed to get addresses${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo "Test completed for user: $RANDOM_USER"
echo ""
