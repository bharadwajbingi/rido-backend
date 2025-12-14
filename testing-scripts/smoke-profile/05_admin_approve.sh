#!/bin/bash
# ============================================================================
# Test: Admin Approve/Reject Documents
# Validates admin approval and rejection workflow for driver documents
# ============================================================================

source "$(dirname "$0")/test-helpers.sh" 2>/dev/null || true

TEST_NAME="Admin Approve/Reject"
echo -e "${BLUE}TEST: $TEST_NAME${NC}"

# Step 1: Register a driver and upload a document
echo "→ Registering driver user..."
DRIVER_USERNAME="test_driver_admin_$(date +%s)"
PASSWORD="TestPass123!"
DRIVER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$DRIVER_USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"DRIVER\"}")

DRIVER_TOKEN=$(extract_token "$DRIVER_RESPONSE")
DRIVER_USER_ID=$(extract_user_id "$DRIVER_RESPONSE")

if [ -z "$DRIVER_TOKEN" ]; then
    echo -e "${RED}❌ Failed to register driver${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Driver registered${NC}"

echo "→ Uploading document for approval..."
DOC_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $DRIVER_TOKEN" \
    -H "X-User-ID: $DRIVER_USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"LICENSE","documentNumber":"DL-APPROVE-TEST","url":"https://example.com/license.pdf"}')

DOC_ID=$(echo "$DOC_RESPONSE" | jq -r '.id // empty')

if [ -z "$DOC_ID" ]; then
    echo -e "${RED}❌ Failed to upload document${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Document uploaded: $DOC_ID${NC}"

# Step 2: Register an admin user
echo "→ Registering admin user..."
ADMIN_USERNAME="test_admin_$(date +%s)"
ADMIN_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")

ADMIN_TOKEN=$(extract_token "$ADMIN_RESPONSE")
ADMIN_USER_ID=$(extract_user_id "$ADMIN_RESPONSE")

if [ -z "$ADMIN_TOKEN" ]; then
    echo -e "${RED}❌ Failed to register admin${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Admin registered${NC}"

# Step 3: Admin approves the document
echo "→ Admin approving document..."
APPROVE_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/admin/driver/$DOC_ID/approve" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "X-User-ID: $ADMIN_USER_ID")

APPROVED_STATUS=$(echo "$APPROVE_RESPONSE" | jq -r '.status // empty')

if [ "$APPROVED_STATUS" != "APPROVED" ]; then
    echo -e "${RED}❌ Document approval failed, status: $APPROVED_STATUS${NC}"
    echo "Response: $APPROVE_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ Document approved successfully${NC}"

# Step 4: Verify status by getting documents as driver
echo "→ Verifying approval as driver..."
DRIVER_DOCS=$(curl -s -X GET "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $DRIVER_TOKEN" \
    -H "X-User-ID: $DRIVER_USER_ID")

VERIFIED_STATUS=$(echo "$DRIVER_DOCS" | jq -r --arg id "$DOC_ID" '.[] | select(.id == $id) | .status // empty')

if [ "$VERIFIED_STATUS" != "APPROVED" ]; then
    echo -e "${RED}❌ Status not updated to APPROVED${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Approval verified${NC}"

# Step 5: Upload another document for rejection test
echo "→ Uploading document for rejection..."
REJECT_DOC_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $DRIVER_TOKEN" \
    -H "X-User-ID: $DRIVER_USER_ID" \
    -H "Content-Type: application/json" \
    -d '{"type":"REGISTRATION","documentNumber":"REG-REJECT-TEST","url":"https://example.com/reg.pdf"}')

REJECT_DOC_ID=$(echo "$REJECT_DOC_RESPONSE" | jq -r '.id // empty')

if [ -z "$REJECT_DOC_ID" ]; then
    echo -e "${RED}❌ Failed to upload document for rejection${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Document uploaded for rejection: $REJECT_DOC_ID${NC}"

# Step 6: Admin rejects the document
echo "→ Admin rejecting document..."
REJECTION_REASON="Document is blurry and unreadable"
REJECT_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/profile/admin/driver/$REJECT_DOC_ID/reject" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "X-User-ID: $ADMIN_USER_ID" \
    -H "Content-Type: application/json" \
    -d "{\"reason\":\"$REJECTION_REASON\"}")

REJECTED_STATUS=$(echo "$REJECT_RESPONSE" | jq -r '.status // empty')
REJECTED_REASON=$(echo "$REJECT_RESPONSE" | jq -r '.reason // empty')

if [ "$REJECTED_STATUS" != "REJECTED" ]; then
    echo -e "${RED}❌ Document rejection failed, status: $REJECTED_STATUS${NC}"
    echo "Response: $REJECT_RESPONSE"
    exit 1
fi

if [ "$REJECTED_REASON" != "$REJECTION_REASON" ]; then
    echo -e "${RED}❌ Rejection reason mismatch${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Document rejected successfully${NC}"
echo "  Reason: $REJECTED_REASON"

# Step 7: Verify rejection as driver
echo "→ Verifying rejection as driver..."
DRIVER_DOCS_FINAL=$(curl -s -X GET "$GATEWAY_URL/profile/driver/documents" \
    -H "Authorization: Bearer $DRIVER_TOKEN" \
    -H "X-User-ID: $DRIVER_USER_ID")

VERIFIED_REJECT_STATUS=$(echo "$DRIVER_DOCS_FINAL" | jq -r --arg id "$REJECT_DOC_ID" '.[] | select(.id == $id) | .status // empty')
VERIFIED_REJECT_REASON=$(echo "$DRIVER_DOCS_FINAL" | jq -r --arg id "$REJECT_DOC_ID" '.[] | select(.id == $id) | .reason // empty')

if [ "$VERIFIED_REJECT_STATUS" != "REJECTED" ]; then
    echo -e "${RED}❌ Status not updated to REJECTED${NC}"
    exit 1
fi

if [ "$VERIFIED_REJECT_REASON" != "$REJECTION_REASON" ]; then
    echo -e "${RED}❌ Rejection reason not preserved${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Rejection verified${NC}"

echo -e "${GREEN}✅ TEST PASSED: $TEST_NAME${NC}"
exit 0
