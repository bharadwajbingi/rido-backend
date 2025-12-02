#!/bin/bash

# 10_verify_max_sessions.sh
# Verifies that a user cannot have more than N active sessions (e.g., 10).

echo "----------------------------------------------------------------"
echo "TEST 10: Max Active Sessions Limit Verification"
echo "----------------------------------------------------------------"

# 1. Create a user
U="max_session_user_bingi"
P="pass123"
echo "Creating user: $U"
curl -s -X POST "http://localhost:8080/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\", \"password\":\"$P\"}" > /dev/null

# 2. Login 6 times (Limit is 5)
echo "Logging in 6 times..."
for i in {1..6}
do
   echo -n "Login $i... "
   LOGIN=$(curl -s -X POST "http://localhost:8080/auth/login" \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"$U\", \"password\":\"$P\"}")
   
   RT=$(echo "$LOGIN" | jq -r '.refreshToken')
   if [ "$RT" != "null" ]; then
      echo "OK"
   else
      echo "FAILED"
   fi
done

# 3. Verify DB count (Should be 5, NOT 6)
echo "----------------------------------------------------------------"
echo "Checking Active Session Count in DB (Should be 5)..."

COUNT=$(docker exec -i infra-postgres-1 psql -U rh_user -d ride_hailing -t -c \
"SELECT COUNT(*) FROM refresh_tokens r JOIN users u ON r.user_id = u.id WHERE u.username = '$U' AND r.revoked = false;")

# Trim whitespace
COUNT=$(echo $COUNT | xargs)

echo "Active Sessions Found: $COUNT"

if [ "$COUNT" -le "5" ]; then
  echo "SUCCESS: Session count is within limit."
else
  echo "FAILURE: Session count exceeded limit!"
fi
echo "----------------------------------------------------------------"
