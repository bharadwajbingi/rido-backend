#!/bin/bash

GATEWAY="http://localhost:8080"
RANDOM_USER="user_$(uuidgen | cut -d'-' -f1)"
PASSWORD="StrongPass123!"
DEVICE_ID="test-device"
USER_AGENT="curl-test"

echo "===================================="
echo "1. REGISTER USER"
echo "===================================="
curl -s -X POST "$GATEWAY/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"$PASSWORD\"}"
echo -e "\n"

echo "===================================="
echo "2. LOGIN USER"
echo "===================================="
LOGIN_RESP=$(curl -s -X POST "$GATEWAY/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: $DEVICE_ID" \
  -H "User-Agent: $USER_AGENT" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"$PASSWORD\"}")

echo $LOGIN_RESP
ACCESS=$(echo $LOGIN_RESP | jq -r '.accessToken')
REFRESH=$(echo $LOGIN_RESP | jq -r '.refreshToken')
USER_ID=$(echo $LOGIN_RESP | jq -r '.userId') # optional

echo -e "\nACCESS TOKEN: $ACCESS\n"

echo "===================================="
echo "3. GET PROFILE (GET /profile/me)"
echo "===================================="
curl -s -X GET "$GATEWAY/profile/me" \
  -H "Authorization: Bearer $ACCESS"
echo -e "\n"

echo "===================================="
echo "4. UPDATE PROFILE"
echo "===================================="
curl -s -X PUT "$GATEWAY/profile/me" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"name":"Bharadwaj Updated","email":"bhara@example.com"}'
echo -e "\n"

echo "===================================="
echo "5. UPLOAD PROFILE PHOTO"
echo "===================================="
curl -s -X POST "$GATEWAY/profile/me/photo" \
  -H "Authorization: Bearer $ACCESS" \
  -F "file=@./sample-photo.jpg"
echo -e "\n"

echo "===================================="
echo "6. ADD RIDER ADDRESS"
echo "===================================="
curl -s -X POST "$GATEWAY/profile/rider/address" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"label":"Home","lat":17.3850,"lng":78.4867}'
echo -e "\n"

echo "===================================="
echo "7. GET RIDER ADDRESSES"
echo "===================================="
curl -s -X GET "$GATEWAY/profile/rider/address" \
  -H "Authorization: Bearer $ACCESS"
echo -e "\n"

echo "===================================="
echo "8. DRIVER DOCUMENT UPLOAD"
echo "===================================="
curl -s -X POST "$GATEWAY/profile/driver/documents" \
  -H "Authorization: Bearer $ACCESS" \
  -F "license=@./sample-license.jpg" \
  -F "rc=@./sample-rc.jpg"
echo -e "\n"

echo "===================================="
echo "9. ADMIN APPROVES DRIVER"
echo "===================================="
curl -s -X POST "$GATEWAY/profile/admin/driver/$USER_ID/approve" \
  -H "X-SYSTEM-KEY: my_admin_secret"
echo -e "\n"

echo "===================================="
echo "10. GET DRIVER STATS"
echo "===================================="
curl -s -X GET "$GATEWAY/profile/driver/$USER_ID/stats" \
  -H "Authorization: Bearer $ACCESS"
echo -e "\n"

echo "===================================="
echo "11. BAN USER (ADMIN)"
echo "===================================="
curl -s -X POST "$GATEWAY/profile/admin/user/$USER_ID/ban" \
  -H "X-SYSTEM-KEY: my_admin_secret"
echo -e "\n"

echo "===================================="
echo "12. VERIFY BANNED USER CANNOT ACCESS"
echo "===================================="
curl -s -X GET "$GATEWAY/profile/me" \
  -H "Authorization: Bearer $ACCESS"
echo -e "\n"
