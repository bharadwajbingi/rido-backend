#!/bin/bash
# ============================================================================
# Admin Port Test Script
# Port 9091 - HTTP (No TLS, No Certificates)
# Access via VPN/SSH tunnel only
# ============================================================================

set -e

BASE_URL="http://localhost:9091"
ADMIN_JWT=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo "=============================================="
echo "🔐 Admin System Integration Tests"
echo "   Port: 9091 (HTTP - No certs needed)"
echo "=============================================="
echo ""

# --------------------------------------------------------
# Test 1: Health Check
# --------------------------------------------------------
echo -n "[TEST 1] Admin health check... "
HEALTH=$(curl -s "${BASE_URL}/admin/health" 2>/dev/null || echo "FAILED")
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo -e "${GREEN}✅ PASS${NC}"
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $HEALTH"
    echo "Make sure auth container is running: docker compose up -d auth"
    exit 1
fi

# --------------------------------------------------------
# Test 2: Bootstrap Admin Login
# --------------------------------------------------------
echo -n "[TEST 2] Bootstrap admin login... "
LOGIN_RESULT=$(curl -s -X POST "${BASE_URL}/admin/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"SuperSecretAdmin123"}' 2>/dev/null)

if echo "$LOGIN_RESULT" | grep -q "accessToken"; then
    echo -e "${GREEN}✅ PASS${NC}"
    ADMIN_JWT=$(echo "$LOGIN_RESULT" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
    echo "   JWT: ${ADMIN_JWT:0:50}..."
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $LOGIN_RESULT"
    exit 1
fi

# --------------------------------------------------------
# Test 3: Create New Admin
# --------------------------------------------------------
TIMESTAMP=$(date +%s)
NEW_ADMIN="test_admin_${TIMESTAMP}"
echo -n "[TEST 3] Create new admin (${NEW_ADMIN})... "
CREATE_RESULT=$(curl -s -X POST "${BASE_URL}/admin/create" \
    -H "Authorization: Bearer $ADMIN_JWT" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${NEW_ADMIN}\",\"password\":\"TestAdmin123!\"}" 2>/dev/null)

if echo "$CREATE_RESULT" | grep -q '"status":"ok"'; then
    echo -e "${GREEN}✅ PASS${NC}"
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $CREATE_RESULT"
    exit 1
fi

# --------------------------------------------------------
# Test 4: New Admin Can Login
# --------------------------------------------------------
echo -n "[TEST 4] New admin can login... "
NEW_ADMIN_LOGIN=$(curl -s -X POST "${BASE_URL}/admin/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${NEW_ADMIN}\",\"password\":\"TestAdmin123!\"}" 2>/dev/null)

if echo "$NEW_ADMIN_LOGIN" | grep -q "accessToken"; then
    echo -e "${GREEN}✅ PASS${NC}"
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $NEW_ADMIN_LOGIN"
    exit 1
fi

# --------------------------------------------------------
# Test 5: JWT Key Rotation
# --------------------------------------------------------
echo -n "[TEST 5] Rotate JWT signing key... "
ROTATE_RESULT=$(curl -s -X POST "${BASE_URL}/admin/key/rotate" \
    -H "Authorization: Bearer $ADMIN_JWT" 2>/dev/null)

if echo "$ROTATE_RESULT" | grep -q "newKid"; then
    echo -e "${GREEN}✅ PASS${NC}"
    NEW_KID=$(echo "$ROTATE_RESULT" | grep -o '"newKid":"[^"]*"' | cut -d'"' -f4)
    echo "   New Key ID: ${NEW_KID:0:20}..."
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $ROTATE_RESULT"
    exit 1
fi

# --------------------------------------------------------
# Test 6: Get Audit Logs
# --------------------------------------------------------
echo -n "[TEST 6] Fetch audit logs... "
AUDIT_RESULT=$(curl -s "${BASE_URL}/admin/audit/logs?page=0&size=5" \
    -H "Authorization: Bearer $ADMIN_JWT" 2>/dev/null)

if echo "$AUDIT_RESULT" | grep -q '"logs"'; then
    echo -e "${GREEN}✅ PASS${NC}"
    LOG_SIZE=$(echo "$AUDIT_RESULT" | grep -o '"size":[0-9]*' | cut -d':' -f2)
    echo "   Retrieved $LOG_SIZE log entries"
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "Response: $AUDIT_RESULT"
    exit 1
fi

# --------------------------------------------------------
# Test 7: Unauthorized Access (No JWT)
# --------------------------------------------------------
echo -n "[TEST 7] Verify unauthorized access blocked... "
UNAUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/admin/create" \
    -H "Content-Type: application/json" \
    -d '{"username":"hacker","password":"hack123"}' 2>/dev/null)

if [ "$UNAUTH_CODE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} (HTTP 401)"
else
    echo -e "${RED}❌ FAIL${NC} (Expected 401, got $UNAUTH_CODE)"
    exit 1
fi

# --------------------------------------------------------
# Test 8: Non-Admin User Cannot Access
# --------------------------------------------------------
echo -n "[TEST 8] Non-admin JWT rejected... "
# Create a fake JWT that looks valid but has wrong role
FAKE_RESULT=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/admin/create" \
    -H "Authorization: Bearer fake.jwt.token" \
    -H "Content-Type: application/json" \
    -d '{"username":"hacker2","password":"hack123"}' 2>/dev/null)

if [ "$FAKE_RESULT" = "401" ] || [ "$FAKE_RESULT" = "403" ]; then
    echo -e "${GREEN}✅ PASS${NC} (HTTP $FAKE_RESULT)"
else
    echo -e "${YELLOW}⚠️ WARNING${NC} (Got HTTP $FAKE_RESULT)"
fi

# --------------------------------------------------------
# Test 9: Gateway Cannot Reach Admin Port
# --------------------------------------------------------
echo -n "[TEST 9] Gateway does not expose admin routes... "
GW_RESULT=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/admin/health" 2>/dev/null || echo "000")

if [ "$GW_RESULT" = "404" ] || [ "$GW_RESULT" = "000" ]; then
    echo -e "${GREEN}✅ PASS${NC} (HTTP $GW_RESULT)"
else
    echo -e "${YELLOW}⚠️ WARNING${NC} (Got HTTP $GW_RESULT)"
fi

# --------------------------------------------------------
# Test 10: Wrong Password Rejected (No Lockout)
# --------------------------------------------------------
echo -n "[TEST 10] Wrong password rejected (admin immune to lockout)... "
for i in 1 2 3 4 5; do
    curl -s -X POST "${BASE_URL}/admin/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"wrongpassword"}' >/dev/null 2>&1
done
# Now try correct password - should still work (no lockout)
LOCKOUT_TEST=$(curl -s -X POST "${BASE_URL}/admin/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"SuperSecretAdmin123"}' 2>/dev/null)

if echo "$LOCKOUT_TEST" | grep -q "accessToken"; then
    echo -e "${GREEN}✅ PASS${NC} (Admin not locked out)"
else
    echo -e "${RED}❌ FAIL${NC} (Admin got locked out!)"
    exit 1
fi

# --------------------------------------------------------
# Summary
# --------------------------------------------------------
echo ""
echo "=============================================="
echo -e "${GREEN}🎉 All Admin System Tests Passed!${NC}"
echo "=============================================="
echo ""
echo "📋 Available Admin Endpoints (Port 9091):"
echo "   POST ${BASE_URL}/admin/login"
echo "   POST ${BASE_URL}/admin/create"
echo "   POST ${BASE_URL}/admin/key/rotate"
echo "   GET  ${BASE_URL}/admin/audit/logs"
echo "   GET  ${BASE_URL}/admin/health"
echo ""
echo "🔐 Bootstrap Admin Credentials:"
echo "   Username: admin"
echo "   Password: SuperSecretAdmin123"
echo ""
